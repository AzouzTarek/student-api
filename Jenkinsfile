pipeline {
  agent any

  environment {
    DOCKER_HUB_CRED = credentials('dockerhub-creds')
    SONAR_TOKEN     = credentials('sonarqube-token')
    SLACK_WEBHOOK   = credentials('slack-webhook')

    IMAGE_NAME   = 'azouztarek/student-api'
    IMAGE_TAG    = "v${env.BUILD_NUMBER}"
    APP_PORT     = "9090"
    DB_CONTAINER = "postgres-student"
    APP_CONTAINER= "student-api"
    NETWORK      = "student-net"

    // Optional: bind to current git commit
    GIT_SHA      = "${env.GIT_COMMIT}"
  }

  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '15'))
    skipDefaultCheckout(true)
  }

  stages {

    stage('Checkout') {
      steps {
        checkout([$class: 'GitSCM',
          branches: [[name: '*/main']],
          userRemoteConfigs: [[url: 'https://github.com/AzouzTarek/student-api.git']]
        ])
      }
    }

    stage('Setup Docker Network & PostgreSQL') {
      steps {
        sh """
          docker network inspect ${NETWORK} >/dev/null 2>&1 || docker network create ${NETWORK}
          docker rm -f ${DB_CONTAINER} >/dev/null 2>&1 || true

          docker run -d --name ${DB_CONTAINER} \\
            --network ${NETWORK} \\
            -e POSTGRES_USER=student \\
            -e POSTGRES_PASSWORD=student \\
            -e POSTGRES_DB=studentdb \\
            -p 5432:5432 \\
            --health-cmd="pg_isready -U student -d studentdb || exit 1" \\
            --health-interval=5s --health-timeout=3s --health-retries=20 \\
            postgres:15
        """

        // Wait for health
        sh '''
          for i in {1..60}; do
            STATUS=$(docker inspect --format='{{json .State.Health.Status}}' '"${DB_CONTAINER}"' 2>/dev/null | tr -d '"')
            if [ "$STATUS" = "healthy" ]; then exit 0; fi
            sleep 2
          done
          echo "Postgres did not become healthy in time"
          docker logs '"${DB_CONTAINER}"' || true
          exit 1
        '''
      }
    }

    stage('Unit Tests') {
      steps {
        sh '''
          export SPRING_DATASOURCE_URL="jdbc:postgresql://'"${DB_CONTAINER}"':5432/studentdb"
          export SPRING_DATASOURCE_USERNAME=student
          export SPRING_DATASOURCE_PASSWORD=student
          ./mvnw -B -Dmaven.test.failure.ignore=false test
        '''
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
        }
      }
    }

    stage('Integration Tests') {
      steps {
        sh '''
          export SPRING_PROFILES_ACTIVE=test
          export SPRING_DATASOURCE_URL="jdbc:postgresql://'"${DB_CONTAINER}"':5432/studentdb"
          export SPRING_DATASOURCE_USERNAME=student
          export SPRING_DATASOURCE_PASSWORD=student
          # Requires failsafe + IT classes (e.g., *IT.java)
          ./mvnw -B -DskipUnitTests=true verify
        '''
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/target/failsafe-reports/*.xml'
        }
      }
    }

    stage('SonarQube Analysis') {
      steps {
        withSonarQubeEnv('SonarQube') {
          withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_AUTH_TOKEN')]) {
            sh """
              ./mvnw sonar:sonar \\
                -Dsonar.projectKey=StudentAPI \\
                -Dsonar.host.url=$SONAR_HOST_URL \\
                -Dsonar.login=$SONAR_AUTH_TOKEN
            """
          }
        }
      }
    }

    stage('Quality Gate') {
      when { expression { return env.CHANGE_ID == null } } // optional: skip for PRs
      steps {
        timeout(time: 3, unit: 'MINUTES') {
          script {
            def qg = waitForQualityGate() // requires SonarQube Scanner for Jenkins
            if (qg.status != 'OK') {
              error "Quality Gate failed: ${qg.status}"
            }
          }
        }
      }
    }

    stage('Build & Docker') {
      steps {
        sh './mvnw -B clean package -DskipTests'
        // Build image with two tags: version + latest
        sh """
          docker build --pull -t ${IMAGE_NAME}:${IMAGE_TAG} -t ${IMAGE_NAME}:latest .
          echo "$DOCKER_HUB_CRED_PSW" | docker login -u "$DOCKER_HUB_CRED_USR" --password-stdin
          docker push ${IMAGE_NAME}:${IMAGE_TAG}
          docker push ${IMAGE_NAME}:latest
          docker logout || true
        """
      }
    }

    stage('Security Scan (Trivy) - Source') {
      steps {
        sh '''
          mkdir -p reports
          trivy fs --exit-code 0 --format table --severity CRITICAL,HIGH . | tee reports/trivy-fs.txt
          trivy fs --exit-code 0 --format json --severity CRITICAL,HIGH . > reports/trivy-fs.json
        '''
        archiveArtifacts artifacts: 'reports/trivy-fs.*', fingerprint: true, allowEmptyArchive: true
      }
    }

    stage('Security Scan (Trivy) - Image') {
      steps {
        sh """
          trivy image --exit-code 0 --format table --severity CRITICAL,HIGH ${IMAGE_NAME}:${IMAGE_TAG} | tee reports/trivy-image.txt
          trivy image --exit-code 0 --format json --severity CRITICAL,HIGH ${IMAGE_NAME}:${IMAGE_TAG} > reports/trivy-image.json
        """
        archiveArtifacts artifacts: 'reports/trivy-image.*', fingerprint: true, allowEmptyArchive: true
      }
    }

    stage('Deployment (Local Docker)') {
      steps {
        sh """
          docker rm -f ${APP_CONTAINER} >/dev/null 2>&1 || true
          docker run -d --name ${APP_CONTAINER} \\
            --network ${NETWORK} \\
            -p ${APP_PORT}:${APP_PORT} \\
            -e SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_CONTAINER}:5432/studentdb \\
            -e SPRING_DATASOURCE_USERNAME=student \\
            -e SPRING_DATASOURCE_PASSWORD=student \\
            -e SERVER_PORT=${APP_PORT} \\
            ${IMAGE_NAME}:${IMAGE_TAG}
        """
      }
    }
  } // stages

  post {
    success {
      script {
        // Slack success
        sh """
          curl -X POST -H 'Content-type: application/json' \\
            --data '{"text":"✅ *Pipeline SUCCESS* for job: ${env.JOB_NAME} #${env.BUILD_NUMBER}\\nImage: ${IMAGE_NAME}:${IMAGE_TAG} pushed and deployed locally on port ${APP_PORT}."}' \\
            "$SLACK_WEBHOOK"
        """
      }
      echo "🎉 Build OK"
    }
    failure {
      script {
        sh "docker logs ${DB_CONTAINER} || true"
        sh """
          curl -X POST -H 'Content-type: application/json' \\
            --data '{"text":"❌ *Pipeline FAILED* for job: ${env.JOB_NAME} #${env.BUILD_NUMBER}"}' \\
            "$SLACK_WEBHOOK"
        """
      }
      echo "❌ Build failed"
    }
    always {
      archiveArtifacts artifacts: 'reports/*', allowEmptyArchive: true
      // Optional cleanup:
      // sh "docker rm -f ${DB_CONTAINER} ${APP_CONTAINER} || true"
    }
  }
}
