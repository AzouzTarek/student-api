pipeline {
  agent any

  environment {
    DOCKER_HUB_CRED = credentials('dockerhub-creds')
    SONAR_TOKEN     = credentials('sonarqube-token')

    IMAGE_NAME   = 'azouztarek/student-api'
    IMAGE_TAG    = "v${env.BUILD_NUMBER}"
    APP_PORT     = "9090"
    DB_CONTAINER = "postgres-student"
    NETWORK      = "student-net"
  }

  stages {

    /* ---------------------------------------- */
    /* 1️⃣ CHECKOUT CODE                        */
    /* ---------------------------------------- */
    stage('Checkout') {
      steps {
        git branch: 'main', url: 'https://github.com/AzouzTarek/student-api.git'
      }
    }

    /* ---------------------------------------- */
    /* 2️⃣ DATABASE (postgres)                   */
    /* ---------------------------------------- */
    stage('Setup Docker Network & PostgreSQL') {
      steps {
        script {
          // Create network if missing
          sh "docker network inspect ${NETWORK} >/dev/null 2>&1 || docker network create ${NETWORK}"

          // Remove any old DB container
          sh "docker rm -f ${DB_CONTAINER} || true"

          // Run PostgreSQL WITHOUT publishing port 5432
          sh """
            docker run -d --name ${DB_CONTAINER} \
              --network ${NETWORK} \
              -e POSTGRES_USER=student \
              -e POSTGRES_PASSWORD=student \
              -e POSTGRES_DB=studentdb \
              postgres:15
          """

          // Wait for DB inside the container
          sh '''
            echo "Waiting for PostgreSQL to be ready..."
            for i in {1..30}; do
              docker exec postgres-student pg_isready -U student -d studentdb && exit 0
              echo "Postgres not ready yet..."
              sleep 2
            done
            echo "❌ PostgreSQL did not start in time"
            exit 1
          '''
        }
      }
    }

    /* ---------------------------------------- */
    /* 3️⃣ BUILD PROJECT + BUILD DOCKER IMAGE    */
    /* ---------------------------------------- */
    stage('Build & Docker') {
      steps {
        // Build using Maven wrapper
        sh './mvnw clean package -DskipTests'

        // Build docker image
        sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."

        // Push to Docker Hub
        sh 'echo "$DOCKER_HUB_CRED_PSW" | docker login -u "$DOCKER_HUB_CRED_USR" --password-stdin'
        sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
      }
    }

    /* ---------------------------------------- */
    /* 4️⃣ TESTS UNITAIRES (via PostgreSQL Docker) */
    /* ---------------------------------------- */
    stage('Tests & Deployment') {
      steps {
        script {

          // Run tests pointing to the DOCKER postgres (not localhost!)
          sh '''
            SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-student:5432/studentdb \
            SPRING_DATASOURCE_USERNAME=student \
            SPRING_DATASOURCE_PASSWORD=student \
            ./mvnw test
          '''

          // Clean old app container
          sh "docker rm -f student-api || true"

          // Run application container
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

    /* ---------------------------------------- */
    /* 5️⃣ SONARQUBE ANALYSIS                   */
    /* ---------------------------------------- */
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

  /* ---------------------------------------- */
  /* 6️⃣ POST ACTIONS                          */
  /* ---------------------------------------- */
  post {
    failure {
      echo "❌ Build failed"
    }
    success {
      echo "🎉 Build succeeded"
    }
  }
}
