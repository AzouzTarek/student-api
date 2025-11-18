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
  }

  stages {

    stage('Checkout') {
      steps {
        git branch: 'main', url: 'https://github.com/AzouzTarek/student-api.git'
      }
    }

    stage('Setup Docker Network & PostgreSQL') {
      steps {
        script {
          sh "docker network inspect ${NETWORK} >/dev/null 2>&1 || docker network create ${NETWORK}"
          sh "docker rm -f ${DB_CONTAINER} || true"

          sh """
            docker run -d --name ${DB_CONTAINER} \
              --network ${NETWORK} \
              -e POSTGRES_USER=student \
              -e POSTGRES_PASSWORD=student \
              -e POSTGRES_DB=studentdb \
              --health-cmd="pg_isready -U student -d studentdb || exit 1" \
              --health-interval=5s --health-timeout=3s --health-retries=20 \
              postgres:15
          """

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
    }

    stage('Unit Tests') {
      steps {
        sh '''
          SPRING_DATASOURCE_URL=jdbc:postgresql://'"${DB_CONTAINER}"':5432/studentdb \
          SPRING_DATASOURCE_USERNAME=student \
          SPRING_DATASOURCE_PASSWORD=student \
          ./mvnw -B -Dmaven.test.failure.ignore=false test
        '''
      }
    }

    stage('Build & Docker') {
      steps {
        sh './mvnw -B clean package -DskipTests'
        sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
        withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
          sh 'echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin'
          sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
          sh 'docker logout'
        }
      }
    }

    stage('Security Scan (Trivy) - Source') {
      steps {
        script {
          sh 'mkdir -p reports'
          // Scan du code source
          sh """
            trivy fs --exit-code 1 --severity CRITICAL,HIGH . | tee reports/trivy-fs.txt
            trivy fs --exit-code 1 --severity CRITICAL,HIGH . -f json -o reports/trivy-fs.json
          """
        }
        archiveArtifacts artifacts: 'reports/*', fingerprint: true
      }
    }

    stage('Security Scan (Trivy) - Image') {
      steps {
        script {
          sh """
            trivy image --exit-code 1 --severity CRITICAL,HIGH ${IMAGE_NAME}:${IMAGE_TAG} | tee reports/trivy-image.txt
            trivy image --exit-code 1 --severity CRITICAL,HIGH ${IMAGE_NAME}:${IMAGE_TAG} -f json -o reports/trivy-image.json
          """
        }
        archiveArtifacts artifacts: 'reports/*', fingerprint: true
      }
    }

    stage('Deployment') {
      steps {
        script {
          sh "docker rm -f ${APP_CONTAINER} || true"
          sh """
            docker run -d --name ${APP_CONTAINER} \
              --network ${NETWORK} \
              -p ${APP_PORT}:${APP_PORT} \
              -e SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_CONTAINER}:5432/studentdb \
              -e SPRING_DATASOURCE_USERNAME=student \
              -e SPRING_DATASOURCE_PASSWORD=student \
              -e SERVER_PORT=${APP_PORT} \
              ${IMAGE_NAME}:${IMAGE_TAG}
          """
        }
      }
    }

    stage('SonarQube Analysis') {
      steps {
        withSonarQubeEnv('SonarQube') {
          withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_AUTH_TOKEN')]) {
            sh """
              ./mvnw sonar:sonar \
                -Dsonar.projectKey=StudentAPI \
                -Dsonar.host.url=$SONAR_HOST_URL \
                -Dsonar.login=$SONAR_AUTH_TOKEN
            """
          }
        }
      }
    }

  } // stages

  post {
    success {
      script {
        sh """
          curl -X POST -H 'Content-type: application/json' \
            --data '{"text":"✅ *Pipeline SUCCESS* for job: ${env.JOB_NAME} #${env.BUILD_NUMBER}\\nImage: ${IMAGE_NAME}:${IMAGE_TAG} pushed and deployed on port ${APP_PORT}."}' \
            "$SLACK_WEBHOOK"
        """
        echo "🎉 Build OK"
      }
    }
    failure {
      script {
        sh "docker logs ${DB_CONTAINER} || true"
        sh """
          curl -X POST -H 'Content-type: application/json' \
            --data '{"text":"❌ *Pipeline FAILED* for job: ${env.JOB_NAME} #${env.BUILD_NUMBER}"}' \
            "$SLACK_WEBHOOK"
        """
        echo "❌ Build failed"
      }
    }
    always {
      archiveArtifacts artifacts: 'reports/*', allowEmptyArchive: true
    }
  }
}
