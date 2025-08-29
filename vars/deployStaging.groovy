import org.xuxi.utils.NotifyUtils

def call() {
    def branches = generateProcessor()
    parallel branches
}

def generateProcessor() {
    def remotes = params.REMOTES
        .split(',')
        .collect { it.trim() }
        .findAll { it }

    def REMOTE_URL = [
        "abenson"   : "https://pwa.abenson-shop.online/",
        "automatic" : "https://pwa-automatic.abenson-shop.online/",
        "electro": "https://pwa-electro.abenson-shop.online/",
        "abensonhome": "http://pwa-abensonhome.abenson-shop.online/",
        "abz": "https://pwa-abz.abenson-shop.online/"
    ]

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
                    propagate: false
                )

                def jobName   = downstream.getProjectName()
                def buildNum  = downstream.number
                def jenkinsUrl = env.JENKINS_URL
                def blueUrl = "${jenkinsUrl}blue/organizations/jenkins/${jobName}/detail/${jobName}/${buildNum}/pipeline"

                if (downstream.getResult() != 'SUCCESS') {
                    NotifyUtils.sendNotification(this, "Deploy [${remote}](${REMOTE_URL[remote]}) failed", 1, blueUrl)
                } else {
                    NotifyUtils.sendNotification(this, "Deploy [${remote}](${REMOTE_URL[remote]}) done", 0, blueUrl)
                }
            }
        }
    }

    return branches
}