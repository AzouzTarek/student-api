pipeline {
    agent any

    environment {
        DOCKER_HUB_CRED = credentials('dockerhub-creds')
        SONAR_TOKEN = credentials('sonarqube-token')
        SLACK_WEBHOOK = credentials('slack-webhook')
        IMAGE_NAME = 'azouztarek/student-api'
        IMAGE_TAG = "v${env.BUILD_NUMBER}"
        APP_PORT = "8081"
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/AzouzTarek/student-api.git'
            }
        }

        stage('Build & Docker') {
            steps {
                sh './mvnw clean package -DskipTests'
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                sh "docker login -u ${DOCKER_HUB_CRED_USR} -p ${DOCKER_HUB_CRED_PSW}"
                sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
            }
        }

        stage('Tests & Local Deployment') {
            steps {
                sh './mvnw test'
        // Supprimer l'ancien conteneur s'il existe pour éviter le conflit
        sh "docker rm -f student-api || true"

                sh "docker run -d --name student-api -p ${APP_PORT}:${APP_PORT} ${IMAGE_NAME}:${IMAGE_TAG}"
            }
        }

stage('Start SonarQube') {
    steps {
        
        echo "Démarrage du conteneur SonarQube..."
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

        // Scan filesystem avec Trivy
        sh "trivy fs ."

        // Scan de l’image Docker
        sh "trivy image ${IMAGE_NAME}:${IMAGE_TAG}"
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
            steps {
                sh """
                git clone https://github.com/AzouzTarek/k8s-manifests.git
                cd k8s-manifests
                sed -i 's|image:.*|image: ${IMAGE_NAME}:${IMAGE_TAG}|' deployment.yaml
                git commit -am 'Update image ${IMAGE_TAG}'
                git push origin main
                """
                sh "argocd app sync student-api"
            }
        }

        stage('Monitoring & Alerting') {
            steps {
                echo "Prometheus et Grafana doivent être configurés sur le cluster Kubernetes."
                echo "Création de dashboards et alertes spécifiques à Spring Boot."
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
