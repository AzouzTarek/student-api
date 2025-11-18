pipeline {
  agent any

  environment {
    DOCKER_HUB_CRED = credentials('dockerhub-creds')   // Username/Password (or token) Jenkins
    SONAR_TOKEN     = credentials('sonarqube-token')

    IMAGE_NAME   = 'azouztarek/student-api'
    IMAGE_TAG    = "v${env.BUILD_NUMBER}"
    APP_PORT     = "9090"
    DB_CONTAINER = "postgres-student"
    NETWORK      = "student-net"
  }

  stages {
    /* 1️⃣ CHECKOUT CODE */
    stage('Checkout') {
      steps {
        git branch: 'main', url: 'https://github.com/AzouzTarek/student-api.git'
      }
    }

    /* 2️⃣ DATABASE (PostgreSQL dans réseau Docker) */
    stage('Setup Docker Network & PostgreSQL') {
      steps {
        script {
          // Créer le réseau s'il n'existe pas
          sh """
            docker network inspect ${NETWORK} >/dev/null 2>&1 || docker network create ${NETWORK}
          """

          // (Re)créer le conteneur Postgres (sans publier de port)
          sh "docker rm -f ${DB_CONTAINER} || true"
          sh """
            docker run -d --name ${DB_CONTAINER} \
              --network ${NETWORK} \
              -e POSTGRES_USER=student \
              -e POSTGRES_PASSWORD=student \
              -e POSTGRES_DB=studentdb \
              postgres:15
          """

          // Attendre la disponibilité
          sh '''
            echo "[wait] Waiting for PostgreSQL readiness..."
            for i in {1..30}; do
              if docker exec '"${DB_CONTAINER}"' pg_isready -U student -d studentdb >/dev/null 2>&1; then
                echo "[wait] PostgreSQL is ready."
                exit 0
              fi
              sleep 2
            done
            echo "❌ PostgreSQL did not start in time"
            exit 1
          '''
        }
      }
    }

    /* 3️⃣ BUILD (Maven) + DOCKER IMAGE */
    stage('Build & Docker') {
      steps {
        // Build Java (skipping tests à ce stade)
        sh './mvnw -B clean package -DskipTests'

        // Build docker image multi-stage (Dockerfile du repo)
        sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."

        // Login & Push
        sh """
          echo "${DOCKER_HUB_CRED_PSW}" | docker login -u "${DOCKER_HUB_CRED_USR}" --password-stdin
          docker push ${IMAGE_NAME}:${IMAGE_TAG}
        """
      }
    }

    /* 4️⃣ TESTS (dans un conteneur Maven joint au même réseau Docker) + DEPLOY */
    stage('Tests & Deployment') {
      steps {
        script {
          // Exécuter les tests dans un conteneur Maven attaché au réseau 'student-net'
          sh """
            docker run --rm --name maven-tests \\
              --network ${NETWORK} \\
              -v "$PWD":/workspace -w /workspace \\
              -e SPRING_DATASOURCE_URL=jdbc:postgresql://${DB_CONTAINER}:5432/studentdb \\
              -e SPRING_DATASOURCE_USERNAME=student \\
              -e SPRING_DATASOURCE_PASSWORD=student \\
              maven:3.9.9-eclipse-temurin-17 \\
              ./mvnw -B test
          """

          // (Re)déployer le conteneur applicatif
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

    /* 5️⃣ SONARQUBE */
    stage('SonarQube Analysis') {
      when { expression { return env.SONAR_TOKEN != null } }
      steps {
        withSonarQubeEnv('SonarQube') {
          withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_AUTH_TOKEN')]) {
            sh '''
              ./mvnw -B sonar:sonar \
                -Dsonar.projectKey=StudentAPI \
                -Dsonar.host.url=$SONAR_HOST_URL \
                -Dsonar.login=$SONAR_AUTH_TOKEN
            '''
          }
        }
      }
    }
  }

  post {
    failure { echo "❌ Build failed" }
    success { echo "🎉 Build succeeded" }
  }
}
