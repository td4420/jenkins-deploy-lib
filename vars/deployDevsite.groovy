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
        "abenson"   : "http://v556pwa.bssdev.cloud/",
        "automatic" : "https://v556pwa-au.bssdev.cloud/",
        "electro": "https://v556pwa-ew.bssdev.cloud/",
        "abensonhome": "https://v556pwa-abh.bssdev.cloud/",
        "abz": "https://v556pwa-abz.bssdev.cloud/"
    ]

    def branches = [:]
    remotes.eachWithIndex { remote, index ->
        branches["Deploy PWA ${remote}"] = {
            node {
                sshagent([env.SSH_CRED]) {
                    def status = sh(
                        script: """
                            ssh -o StrictHostKeyChecking=no -p ${env.SSH_PORT} \
                            ${env.SSH_USER_NAME}@${env.SSH_REMOTE} \
                            "cd public_html && bash ./deploy.sh '${remote}' '${params.BRANCH}'"
                        """,
                        returnStatus: true
                    )
                    
                    if (status == 0) {
                        NotifyUtils.sendNotification(this, "${params.TAG_NAMES} Deploy dev site [${remote}](${REMOTE_URL[remote]}) failed", 1, env.BLUE_LINK)
                    } else {
                        NotifyUtils.sendNotification(this, "${params.TAG_NAMES} Deploy dev site [${remote}](${REMOTE_URL[remote]}) done", 0, env.BLUE_LINK)
                    }
                }
            }
        }
    }

    return branches
}