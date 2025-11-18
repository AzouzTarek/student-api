pipeline {
  agent any

  environment {
    DOCKER_HUB_CRED = credentials('dockerhub-creds')
    SONAR_TOKEN     = credentials('sonarqube-token')
    
    IMAGE_NAME = 'azouztarek/student-api'
    IMAGE_TAG  = "v${env.BUILD_NUMBER}"
    APP_PORT   = "9090"
    DB_CONTAINER = "postgres-student"
    NETWORK = "student-net"
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
              -p 5432:5432 \
              postgres:15
          """

          sh '''
            for i in {1..30}; do
              if nc -z localhost 5432; then exit 0; fi
              sleep 2
            done
            exit 1
          '''
        }
      }
    }

    stage('Build & Docker') {
      steps {
        sh './mvnw clean package -DskipTests'

        sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."

        sh 'echo "$DOCKER_HUB_CRED_PSW" | docker login -u "$DOCKER_HUB_CRED_USR" --password-stdin'
        sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
      }
    }

    stage('Tests & Deployment') {
      steps {
        script {
          sh '''
            SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/studentdb \
            SPRING_DATASOURCE_USERNAME=student \
            SPRING_DATASOURCE_PASSWORD=student \
            ./mvnw test
          '''

          sh "docker rm -f student-api || true"

          sh """
            docker run -d --name student-api \
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

  }

  post {
    failure {
      echo "❌ Build failed"
    }
    success {
      echo "🎉 Build OK"
    }
  }
}
