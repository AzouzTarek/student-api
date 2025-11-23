
pipeline {
  agent any

  options {
    skipDefaultCheckout(true)
    timestamps()
  }

  environment {
    JAVA_HOME = "/usr/lib/jvm/java-1.17.0-openjdk-amd64"
    PATH = "${JAVA_HOME}/bin:${env.PATH}"

    DOCKER_HUB_CRED = credentials('dockerhub-creds')
    SONAR_TOKEN     = credentials('sonarqube-token')

    IMAGE_NAME    = 'azouztarek/student-api'
    IMAGE_TAG     = "v${env.BUILD_NUMBER}"
    APP_PORT      = "9090"
    DB_CONTAINER  = "postgres-student"
    APP_CONTAINER = "student-api"
    NETWORK       = "student-net"

    // Dossier cache Trivy (pour accélérer les scans)
    TRIVY_CACHE   = "${env.WORKSPACE}/.trivy-cache"
  }

  stages {
    stage('Java Check') {
      steps {
        sh 'echo "JAVA_HOME=$JAVA_HOME"'
        sh 'java -version'
      }
    }

    stage('Checkout') {
      steps {
        git branch: 'main', url: 'https://github.com/AzouzTarek/student-api.git'
      }
    }

    stage('Setup Docker Network & PostgreSQL') {
      steps {
        script {
          // Créer le réseau s'il n'existe pas
          sh "docker network inspect ${NETWORK} >/dev/null 2>&1 || docker network create ${NETWORK}"

          // (Re)création du conteneur Postgres avec healthcheck
          sh "docker rm -f ${DB_CONTAINER} || true"
          sh "docker volume create ${DB_CONTAINER}-data || true"
          sh """
            docker run -d --name ${DB_CONTAINER} \
              --network ${NETWORK} \
              -v ${DB_CONTAINER}-data:/var/lib/postgresql/data \
              -e POSTGRES_USER=student \
              -e POSTGRES_PASSWORD=student \
              -e POSTGRES_DB=studentdb \
              --health-cmd='pg_isready -U student -d studentdb' \
              --health-interval=5s --health-retries=20 --health-timeout=3s \
              postgres:15
          """
          // Attendre le healthy
          sh '''
            echo "⏳ Waiting for PostgreSQL (health=healthy)..."
            for i in $(seq 1 30); do
              status=$(docker inspect --format='{{json .State.Health.Status}}' postgres-student | tr -d '"')
              [ "$status" = "healthy" ] && echo "🔥 PostgreSQL is READY!" && exit 0
              sleep 2
            done
            echo "❌ PostgreSQL did not become healthy in time" >&2
            exit 1
          '''
        }
      }
    }

    stage('Unit Tests') {
      steps {
        sh '''
          ./mvnw -B -Dmaven.test.failure.ignore=false \
            -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5432/studentdb \
            -Dspring.datasource.username=student \
            -Dspring.datasource.password=student \
            -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
            test
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
          // Optionnel: tag latest pour usage par défaut
          sh "docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${IMAGE_NAME}:latest"
          sh "docker push ${IMAGE_NAME}:latest"
          sh 'docker logout'
        }
      }
    }

    stage('Security Scan (Trivy - containerized)') {
      steps {
        script {
          sh 'mkdir -p reports'
          sh 'mkdir -p "${TRIVY_CACHE}"'

          // Scan du workspace (filesystem)
          sh """
            docker run --rm \
              -v ${pwd()}:/src \
              -v /var/run/docker.sock:/var/run/docker.sock \
              -v ${TRIVY_CACHE}:/root/.cache/ \
              aquasec/trivy:latest fs --severity CRITICAL,HIGH /src -f table \
              | tee reports/trivy-source.txt
          """

          // Scan de l'image Docker
          sh """
            docker run --rm \
              -v /var/run/docker.sock:/var/run/docker.sock \
              -v ${TRIVY_CACHE}:/root/.cache/ \
              aquasec/trivy:latest image --severity CRITICAL,HIGH ${IMAGE_NAME}:${IMAGE_TAG} -f table \
              | tee reports/trivy-image.txt
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
              --restart=always \
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

    stage('SonarQube Analysis (containerized)') {
      steps {
        withSonarQubeEnv('SonarQube') {
          withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_AUTH_TOKEN')]) {
            // Analyse via conteneur officiel SonarScanner
            sh """
              docker run --rm \
                -v ${pwd()}:/usr/src \
                -e SONAR_HOST_URL=$SONAR_HOST_URL \
                sonarsource/sonar-scanner-cli:latest \
                -Dsonar.projectKey=StudentAPI \
                -Dsonar.sources=/usr/src \
                -Dsonar.host.url=$SONAR_HOST_URL \
                -Dsonar.login=$SONAR_AUTH_TOKEN
            """
          }
        }
      }
    }
  }

  post {
    success {
      echo "✅ Pipeline SUCCESS"
    }
    failure {
      echo "❌ Pipeline FAILED"
    }
    always {
      archiveArtifacts artifacts: 'reports/*', allowEmptyArchive: true
      // Nettoyage léger pour éviter la croissance
      sh 'docker image prune -f || true'
      sh 'docker container prune -f || true'
    }
  }
}
