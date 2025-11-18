pipeline {
  agent any

  options {
    // Optional: skip implicit SCM checkout so we only use our 'Checkout' stage
    skipDefaultCheckout(true)
  }

  environment {
    DOCKER_HUB_CRED = credentials('dockerhub-creds')
    SONAR_TOKEN     = credentials('sonarqube-token')

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
          sh "docker volume create ${DB_CONTAINER}-data || true"

          sh """
            docker run -d --name ${DB_CONTAINER} \
              --network ${NETWORK} \
              -v ${DB_CONTAINER}-data:/var/lib/postgresql/data \
              -e POSTGRES_USER=student \
              -e POSTGRES_PASSWORD=student \
              -e POSTGRES_DB=studentdb \
              postgres:15
          """

          // Wait until Postgres is ready
          sh '''
            echo "⏳ Waiting for PostgreSQL to become ready..."
            until docker exec postgres-student pg_isready -U student -d studentdb; do
              sleep 2
            done
            echo "🔥 PostgreSQL is READY!"
          '''
        }
      }
    }

    stage('Unit Tests') {
      steps {
        // Activate the 'test' profile so tests pick up src/test/resources/application-test.properties
        sh '''
          export SPRING_PROFILES_ACTIVE=test
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

    stage('Security Scan (Trivy)') {
      steps {
        script {
          sh 'mkdir -p reports'
          sh """
            trivy fs --severity CRITICAL,HIGH . -f table | tee reports/trivy-source.txt
            trivy image --severity CRITICAL,HIGH ${IMAGE_NAME}:${IMAGE_TAG} -f table | tee reports/trivy-image.txt
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
      withCredentials([string(credentialsId: 'slack-webhook', variable: 'SLACK')]) {
        sh """
          curl -X POST -H 'Content-type: application/json' \
          --data @- "$SLACK" <<'JSON'
          {
            "text": "✅ *Pipeline SUCCESS* - Job: ${JOB_NAME} #${BUILD_NUMBER} - Image deployed on port ${APP_PORT}"
          }
JSON
        """
      }
      echo "🎉 Build OK"
    }

    failure {
      sh "docker logs ${DB_CONTAINER} || true"
      withCredentials([string(credentialsId: 'slack-webhook', variable: 'SLACK')]) {
        sh """
          curl -X POST -H 'Content-type: application/json' \
          --data @- "$SLACK" <<'JSON'
          {
            "text": "❌ *Pipeline FAILED* - Job: ${JOB_NAME} #${BUILD_NUMBER}"
          }
JSON
        """
      }
      echo "❌ Build failed"
    }

    always {
      archiveArtifacts artifacts: 'reports/*', allowEmptyArchive: true
    }
  }
}
