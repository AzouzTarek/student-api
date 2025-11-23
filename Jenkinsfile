
pipeline {
  agent any

  options { skipDefaultCheckout(true) }

  environment {
    JAVA_HOME = "/usr/lib/jvm/java-1.17.0-openjdk-amd64"
    PATH = "${JAVA_HOME}/bin:${env.PATH}"

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

    // --- Check Java avant checkout (mvnw non disponible ici)
    stage('Java Check (pre-checkout)') {
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

    // --- mvnw est présent maintenant
    stage('Java/Maven Wrapper Check (post-checkout)') {
      steps {
        sh './mvnw -version'
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
              -p 5432:5432 \
              -v ${DB_CONTAINER}-data:/var/lib/postgresql/data \
              -e POSTGRES_USER=student \
              -e POSTGRES_PASSWORD=student \
              -e POSTGRES_DB=studentdb \
              postgres:15
          """

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
          sh 'docker logout'
        }
      }
    }

    // --- Trivy depuis le conteneur officiel (pas besoin d’installation locale)
    stage('Security Scan (Trivy)') {
      steps {
        script {
          sh 'mkdir -p reports'

          // Scan du code (filesystem)
          sh """
            docker run --rm \
              -v "$PWD":/workspace \
              -v /var/lib/jenkins/.cache/trivy:/root/.cache/ \
              aquasec/trivy:0.56.0 fs --severity CRITICAL,HIGH /workspace -f table \
              | tee reports/trivy-source.txt
          """

          // Scan de l'image construite
          sh """
            docker run --rm \
              -v /var/lib/jenkins/.cache/trivy:/root/.cache/ \
              aquasec/trivy:0.56.0 image --severity CRITICAL,HIGH ${IMAGE_NAME}:${IMAGE_TAG} -f table \
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

    // --- Sonar : à choisir (Plugin Maven OU Scanner CLI)
    stage('SonarQube Analysis') {
      steps {
        withSonarQubeEnv('SonarQube') {
          withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_AUTH_TOKEN')]) {
            // Si tu ajoutes le plugin Sonar dans pom.xml :
            sh """
              ./mvnw -B sonar:sonar \
                -Dsonar.projectKey=StudentAPI \
                -Dsonar.host.url=$SONAR_HOST_URL \
                -Dsonar.login=$SONAR_AUTH_TOKEN
            """
            // Sinon, remplace ci-dessus par le scanner CLI en Docker :
            // sh """
            //   docker run --rm \
            //     -v "$PWD":/src \
            //     -e SONAR_HOST_URL="$SONAR_HOST_URL" \
            //     sonarsource/sonar-scanner-cli:5 \
            //     sonar-scanner \
            //       -Dsonar.projectKey=StudentAPI \
            //       -Dsonar.sources=/src \
            //       -Dsonar.java.binaries=/src/target/classes \
            //       -Dsonar.login=$SONAR_AUTH_TOKEN
            // """
          }
        }
      }
    }
  }

  post {
    success {
      withCredentials([string(credentialsId: 'slack-webhook', variable: 'SLACK')]) {
        writeFile file: 'payload.json', text: """{
          "text": "✅ *Pipeline SUCCESS* - Job: ${JOB_NAME} #${BUILD_NUMBER} - Image deployed on port ${APP_PORT}"
        }"""
        sh 'curl --fail-with-body -sS -X POST -H "Content-type: application/json" --data-binary @payload.json "$SLACK"'
      }
      echo "🎉 Build OK"
    }
    failure {
      sh "docker logs ${DB_CONTAINER} || true"
      withCredentials([string(credentialsId: 'slack-webhook', variable: 'SLACK')]) {
        writeFile file: 'payload.json', text: """{
          "text": "❌ *Pipeline FAILED* - Job: ${JOB_NAME} #${BUILD_NUMBER}"
        }"""
        sh 'curl --fail-with-body -sS -X POST -H "Content-type: application/json" --data-binary @payload.json "$SLACK"'
      }
      echo "❌ Build failed"
    }
    always {
      archiveArtifacts artifacts: 'reports/*', allowEmptyArchive: true
    }
  }
}
