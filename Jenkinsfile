pipeline {

    agent { label 'jdk21' }
    
    environment {
        GIT_CREDENTIAL_ID = 'github'
        DOCKERFILE_PATH = 'src/main/docker/Dockerfile.native'
        GIT_REPO = 'git@github.com:ceoslab/titan-lab-nexus.git'
        APP_NAME = 'titan-lab-nexus'
        WORKSPACE_PATH = "${WORKSPACE}/${env.APP_NAME}/${params.RELEASE_VERSION}"
        NEXUS_USER = 'admin'
        NEXUS_PASSWORD = 'admin123'
        NEXUS_IP = '172.16.4.177'
        NEXUS_PORT = '8081'
        NEXUS_REPO = 'registry.quarkus.io'
        NEXUS_LOGIN = 'nexusCredential'
    }
    
    parameters {
        string(name: 'GIT_BRANCH', description: 'Git branch', defaultValue: 'master')
        string(name: 'RELEASE_VERSION', description: 'Release version')
    }
    
    stages {
        stage('git-checkout') {
            steps {
                checkout([$class: 'GitSCM', branches: [[name: params.GIT_BRANCH]], extensions: [[$class: 'WipeWorkspace']], userRemoteConfigs: [[credentialsId: env.GIT_CREDENTIAL_ID, url: env.GIT_REPO]]])
            }
        }
        stage('check-release-version') {
            steps {
                script {
                    def tagExists = sh(script: "git ls-remote --tags origin ${params.RELEASE_VERSION} | awk '{print \$2}'").trim() == "refs/tags/${params.RELEASE_VERSION}"
                    if (tagExists) {
                        error("Release version ${params.RELEASE_VERSION} already exists. Aborting!")
                    }
                }
            }
        }
        stage('ecr-repository-ensure') {
            steps {
                sh(script: "aws ecr describe-repositories --repository-names ${env.APP_NAME} > /dev/null 2>&1 || aws ecr create-repository --repository-name ${env.APP_NAME} > /dev/null")
                sh(script: "aws ecr set-repository-policy --repository-name ${env.APP_NAME} --policy-text '${env.AMAZON_ECR_REPOSITORY_POLICY}'")
            }
        }
        stage('maven-build') {
            steps {
                sh "./mvnw -s settings.xml -DskipTests install"
            }
        }
        stage('docker-image-release') {
            steps {
                script {
                    docker.withRegistry("https://${env.DOCKER_REGISTRY_URL}", env.DOCKER_REGISTRY_CREDENTIALS_ID) {
                        def dockerImage = docker.build("${env.APP_NAME}:${params.RELEASE_VERSION}", "-f ${env.DOCKERFILE_PATH} .")
                        dockerImage.push()
                    }
                }
            }
        }
        stage('create-git-tag') {
            steps {
                sh 'git config --global user.email "ceoslabci@ceoslab.com.br"'
                sh 'git config --global user.name "Jenkins"'
                sh "git tag -a ${params.RELEASE_VERSION} -m 'Creating tag ${params.RELEASE_VERSION} from Jenkins Build: ${env.BUILD_URL}.'"
                sh "git push origin ${params.RELEASE_VERSION}"
            }
        }
    }
}
