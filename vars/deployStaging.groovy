import org.xuxi.utils.NotifyUtils

def call() {
    stage('Start Process') {
        steps {
            script {
                def jobName   = env.JOB_NAME
                def buildNum  = env.BUILD_NUMBER
                def jenkinsUrl = env.JENKINS_URL
                
                env.BLUE_LINK = "${jenkinsUrl}blue/organizations/jenkins/${jobName}/detail/${jobName}/${buildNum}/pipeline"
                NotifyUtils.sendNotification(this, "Deployment started", 999, env.BLUE_LINK)
            }
        }
    }

    stage('Deploy staging') {
        steps {
            script {
                def branches = generateProcessor()
                parallel branches
            }
        }
    }
}

def generateProcessor() {
    def remotes = params.REMOTES
        .split(',')
        .collect { it.trim() }
        .findAll { it }

    def branches = [:]
    remotes.eachWithIndex { remote, index ->
        branches["Deploy PWA ${remote}"] = {
            node {
                def downstream = build(
                    job: env.TRIGGER_JOB_NAME,
                    parameters: [
                        string(name: 'REMOTE', value: remote),
                        string(name: 'BRANCH', value: params.BRANCH),
                    ],
                    wait: true,
                    propagate: true
                )

                def jobName   = downstream.getProjectName()
                def buildNum  = downstream.number
                def jenkinsUrl = env.JENKINS_URL
                def blueUrl = "${jenkinsUrl}blue/organizations/jenkins/${jobName}/detail/${jobName}/${buildNum}/pipeline"

                if (downstream.getResult() != 'SUCCESS') {
                    NotifyUtils.sendNotification(this, "⚠️ Deploy ${remote} failed", 1, blueUrl)
                } else {
                    NotifyUtils.sendNotification(this, "✅ Deploy ${remote} succeeded", 0, blueUrl)
                }
            }
        }
    }

    return branches
}