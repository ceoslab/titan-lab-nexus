// Sets the git branch from job parameters.
def gitBranch = params.GIT_BRANCH

// Sets the release version from job parameters.
def releaseVersion = params.RELEASE_VERSION

// Defines the Jenkins node.
def jenkinsNode = 'jdk21'

// Defines the git credential ID.
def gitCredentialId = 'github'

// Defines the git repository.
def gitRepo = 'git@github.com:ceoslab/titan-lab-nexus.git'

// Defines the application name.
def appName = 'titan-lab-nexus'

// Defines the Dockerfile path.
def dockerFilePath = 'src/main/docker/Dockerfile.native'

// Defines the workspace path.
def workspacePath = "${env.WORKSPACE_VOLUME}/${appName}/${releaseVersion}"

// Define the Jenkins pipeline.
node(jenkinsNode) {

  ws(workspacePath) {

    // Define the color of the output.
    ansiColor('xterm') {

      // Checkouts the git repository.
      stage('git-checkout') {
        checkout scmGit(branches: [[name: gitBranch]], extensions: [[$class: 'WipeWorkspace']], userRemoteConfigs: [[credentialsId: gitCredentialId, url: gitRepo]])
      }

      // Checks if a Git tag already exists for this release version.
      stage('check-release-version') {
        def tagExists = sh(returnStdout: true, script: "git ls-remote --tags origin ${releaseVersion} | awk '{print \$2}'").trim() == "refs/tags/${releaseVersion}"

        if (tagExists) {
          error("Release version ${releaseVersion} already exists. Aborting!")
        }
      }

      // Ensures Amazon ECR repository exists.
      stage('ecr-repository-ensure') {
        sh(script: "aws ecr describe-repositories --repository-names ${appName} > /dev/null 2>&1 || aws ecr create-repository --repository-name ${appName} > /dev/null")
        sh(script: "aws ecr set-repository-policy --repository-name ${appName} --policy-text '${env.AMAZON_ECR_REPOSITORY_POLICY}'")
      }

      // Creates the build artifact.
      stage('maven-build') {
        sh("./mvnw package -Pnative -s settings.xml")
      }

      docker.withRegistry("https://${env.DOCKER_REGISTRY_URL}", env.DOCKER_REGISTRY_CREDENTIALS_ID) {
        // Builds the Docker image and pushes to Amazon ECR.
        stage('docker-image-release') {
          def dockerImage = docker.build("${appName}:${releaseVersion}", "-f ${dockerFilePath} .")
          dockerImage.push()
        }
      }

      // Creates a Git tag for this release version.
      stage('create-git-tag') {
        sh('git config --global user.email "ceoslabci@ceoslab.com.br"')
        sh('git config --global user.name "Jenkins"')
        sh("git tag -a ${releaseVersion} -m 'Creating tag ${releaseVersion} from Jenkins Build: ${env.BUILD_URL}.'")
        sh("git push origin ${releaseVersion}")
      }
    }
  }
}