pipeline {
  agent any

  environment {
    DOCKER_HUB_CRED = credentials('dockerhub-creds')
    SONAR_TOKEN     = credentials('sonarqube-token')
    SLACK_WEBHOOK   = credentials('slack-webhook')
    IMAGE_NAME      = 'azouztarek/student-api'
    IMAGE_TAG       = "v${env.BUILD_NUMBER}"
    APP_PORT        = "9090"                  // ton app.properties a 9090 ; aligne ici
    DB_CONTAINER    = "postgres-student"
    NETWORK         = "student-net"
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
              --health-cmd="pg_isready -U student -d studentdb || exit 1" \
              --health-interval=5s --health-retries=20 --health-timeout=3s \
              postgres:15
          """

          // Attendre que Postgres réponde côté host (pour les tests Maven)
          sh '''
            for i in {1..30}; do
              if nc -z localhost 5432; then
                echo "Postgres is ready on localhost:5432"; exit 0
              fi
              echo "Waiting for Postgres..."; sleep 2
            done
            echo "Postgres not reachable on localhost:5432"; exit 1
          '''
        }
      }
    }

    stage('Build & Docker') {
      steps {
        // Build Maven sur le host (rapide, cache local Jenkins)
        sh './mvnw clean package -DskipTests'

        // Build image : retry + cache m2 pour éviter les 500 Central
        sh """
          DOCKER_BUILDKIT=1 docker build \
            --build-arg MAVEN_ARGS='-B -q -DskipTests package' \
            -t ${IMAGE_NAME}:${IMAGE_TAG} -f Dockerfile .
        """

        // Login sécurisé à Docker Hub
        sh 'echo "$DOCKER_HUB_CRED_PSW" | docker login -u "$DOCKER_HUB_CRED_USR" --password-stdin'

        sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
      }
    }

    stage('Tests & Deployment') {
      steps {
        script {
          // Lancer les tests sur le host Jenkins en pointant la DB locale
          sh '''
            SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/studentdb \
            SPRING_DATASOURCE_USERNAME=student \
            SPRING_DATASOURCE_PASSWORD=student \
            SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.PostgreSQLDialect \
            ./mvnw test
          '''

          // Nettoyer ancien conteneur applicatif
          sh "docker rm -f student-api || true"

          // Lancer l'app dans le même réseau que la DB.
          // IMPORTANT: l'app en conteneur doit viser 'postgres-student:5432'
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

    stage('Start SonarQube') {
      steps {
        sh "docker rm -f sonarqube || true"
        sh """
          docker run -d --name sonarqube \
            -p 9000:9000 \
            -e SONAR_ES_BOOTSTRAP_CHECKS_DISABLE=true \
            sonarqube:community
        """
        echo "Attente que SonarQube soit prêt..."
        sh "sleep 30"
      }
    }

    stage('Code Quality & Security') {
      steps {
        withSonarQubeEnv('SonarQube') {
          sh """
            sonar-scanner \
              -Dsonar.projectKey=StudentAPI \
              -Dsonar.sources=. \
              -Dsonar.java.binaries=target \
              -Dsonar.login=$SONAR_TOKEN
          """
        }
        sh "trivy fs . || true"          // ne casse pas le build si vulnérabilités
        sh "trivy image ${IMAGE_NAME}:${IMAGE_TAG} || true"
      }
    }

    stage('Notifications') {
      steps {
        slackSend(
          channel: '#jenkins',
          color: 'good',
          message: "Build ${env.BUILD_NUMBER} terminé avec succès !",
          tokenCredentialId: 'slack-webhook'
        )
      }
    }

    stage('GitOps Deployment') {
      when { expression { return false } } // désactivé par défaut tant que tout n’est pas prêt
      steps {
        sh """
          git clone https://github.com/AzouzTarek/k8s-manifests.git
          cd k8s-manifests
          sed -i 's|image:.*|image: ${IMAGE_NAME}:${IMAGE_TAG}|' deployment.yaml
          git commit -am 'Update image ${IMAGE_TAG}' || true
          git push origin main
          argocd app sync student-api
        """
      }
    }

    stage('Monitoring & Alerting') {
      steps {
        echo "Prometheus et Grafana doivent être configurés pour surveiller student-api"
      }
    }
  }

  post {
    failure {
      slackSend(
        channel: '#jenkins',
        color: 'danger',
        message: "Build ${env.BUILD_NUMBER} a échoué !",
        tokenCredentialId: 'slack-webhook'
      )
    }
  }
}
