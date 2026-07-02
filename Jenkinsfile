pipeline {
    agent any

    environment {
        ECR_REGISTRY   = '379220350808.dkr.ecr.us-west-2.amazonaws.com'
        ECR_REPOSITORY = 'service-catalogue'
        AWS_REGION     = 'us-west-2'
        IMAGE_TAG      = "${env.GIT_COMMIT?.take(7) ?: 'latest'}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Login to ECR') {
            steps {
                withCredentials([[$class: 'AmazonWebServicesCredentialsBinding',
                                  credentialsId: 'aws-credentials']]) {
                    sh '''
                        aws ecr get-login-password --region $AWS_REGION | \
                        docker login --username AWS --password-stdin $ECR_REGISTRY
                    '''
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh """
                    docker build -t ${ECR_REGISTRY}/${ECR_REPOSITORY}:latest .
                    docker tag ${ECR_REGISTRY}/${ECR_REPOSITORY}:latest \
                               ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}
                """
            }
        }

        stage('Push to ECR') {
            steps {
                sh """
                    docker push ${ECR_REGISTRY}/${ECR_REPOSITORY}:latest
                    docker push ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG}
                """
            }
        }
    }

    post {
        always {
            sh "docker rmi ${ECR_REGISTRY}/${ECR_REPOSITORY}:latest || true"
            sh "docker rmi ${ECR_REGISTRY}/${ECR_REPOSITORY}:${IMAGE_TAG} || true"
        }
        success {
            echo '✅ Image pushed to ECR successfully!'
        }
        failure {
            echo '❌ Pipeline failed!'
        }
    }
}
