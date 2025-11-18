pipeline {
  agent any

  environment {
    // Credentials Jenkins (Manage Credentials)
    DOCKER_HUB_CRED = credentials('dockerhub-creds')      // username+password
    SONAR_TOKEN     = credentials('sonarqube-token')       // secret text
    SLACK_WEBHOOK   = credentials('slack-webhook')         // si tu utilises un webhook direct (Option B)

    // Paramètres applicatifs
    IMAGE_NAME   = 'azouztarek/student-api'
    IMAGE_TAG    = "v${env.BUILD_NUMBER}"
    APP_PORT     = "9090"                                  // aligné avec application.properties
    DB_CONTAINER = "postgres-student"
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
          // Créer le réseau si absent
          sh "docker network inspect ${NETWORK} >/dev/null 2>&1 || docker network create ${NETWORK}"

          // Nettoyer ancien conteneur DB
          sh "docker rm -f ${DB_CONTAINER} || true"

          // Lancer Postgres avec healthcheck correct
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
        // Build Maven sur l’agent (rapide, cache .m2 local Jenkins)
        sh './mvnw clean package -DskipTests'

        // (Option simple) Construire l'image à partir du jar déjà produit
        // Assure-toi que ton Dockerfile copie bien target/*.jar (voir note en bas)
        sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."

        // Docker login sécurisé
        sh 'echo "$DOCKER_HUB_CRED_PSW" | docker login -u "$DOCKER_HUB_CRED_USR" --password-stdin'

        // Push
        sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
      }
    }

    stage('Tests & Deployment') {
      steps {
        script {
          // Lancer les tests en pointant sur la DB locale (host)
          sh '''
            SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/studentdb \
            SPRING_DATASOURCE_USERNAME=student \
            SPRING_DATASOURCE_PASSWORD=student \
            ./mvnw test
          '''

          // Nettoyer ancien conteneur applicatif
          sh "docker rm -f student-api || true"

          // Lancer l'app dans le même réseau que la DB.
          // IMPORTANT: dans le conteneur, la DB est joignable via le nom du conteneur Postgres.
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
        // Redémarrer un Sonar local (si tu l’auto-héberges dans cette pipeline)
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
        // Sonar via Maven plugin (pas de sonar-scanner requis sur l'agent)
        withSonarQubeEnv('SonarQube') {
          withCredentials([string(credentialsId: 'sonarqube-token', variable: 'SONAR_AUTH_TOKEN')]) {
            sh """
              ./mvnw -B -DskipTests \
                -Dsonar.projectKey=StudentAPI \
                -Dsonar.host.url=$SONAR_HOST_URL \
                -Dsonar.login=$SONAR_AUTH_TOKEN \
                sonar:sonar
            """
          }
        }

        // Trivy via conteneur (ne casse pas le build si vulnérabilités)
        sh '''
          docker run --rm -v "$PWD":/src aquasec/trivy:latest fs /src || true
          docker run --rm aquasec/trivy:latest image '"'"${IMAGE_NAME}:${IMAGE_TAG}"'" || true
        '''
      }
    }

    // (Optionnel) Quality Gate : nécessite le webhook Sonar -> Jenkins correctement configuré
    // stage('Quality Gate') {
    //   steps {
    //     timeout(time: 5, unit: 'MINUTES') {
    //       waitForQualityGate()
    //     }
    //   }
    // }

    stage('Notifications') {
      steps {
        // Option A (plugin Slack configuré côté Jenkins : workspace + token global)
        slackSend(
          channel: '#jenkins',
          color: 'good',
          message: "✅ ${env.JOB_NAME} #${env.BUILD_NUMBER} - Build & Tests OK, image: ${IMAGE_NAME}:${IMAGE_TAG}"
        )

        // Option B (Webhook direct si tu n'utilises pas le plugin) :
        // withCredentials([string(credentialsId: 'slack-webhook', variable: 'WEBHOOK')]) {
        //   sh 'curl -X POST -H "Content-type: application/json" --data "{\"text\":\"✅ ${JOB_NAME} #${BUILD_NUMBER} OK\"}" "$WEBHOOK"'
        // }
      }
    }

    stage('GitOps Deployment') {
      when { expression { return false } } // Désactivé par défaut tant que tout n’est pas finalisé
      steps {
        sh """
          git clone https://github.com/AzouzTarek/k8s-manifests.git
          cd k8s-manifests
          sed -i 's|image: .*|image: ${IMAGE_NAME}:${IMAGE_TAG}|' deployment.yaml
          git commit -am 'Update image ${IMAGE_TAG}' || true
          git push origin main
          argocd app sync student-api
        """
      }
    }

    stage('Monitoring & Alerting') {
      steps {
        echo "Prometheus / Grafana : à configurer pour surveiller student-api (dashboards, alerting)."
      }
    }
  }

  post {
    failure {
      // Option A (plugin Slack configuré)
      slackSend(
        channel: '#jenkins',
        color: 'danger',
        message: "❌ ${env.JOB_NAME} #${env.BUILD_NUMBER} — Build KO. Voir logs Jenkins."
      )

      // Option B (Webhook direct)
      // withCredentials([string(credentialsId: 'slack-webhook', variable: 'WEBHOOK')]) {
      //   sh 'curl -X POST -H "Content-type: application/json" --data "{\"text\":\"❌ ${JOB_NAME} #${BUILD_NUMBER} KO — voir logs\"}" "$WEBHOOK"'
      // }
    }
  }
}

