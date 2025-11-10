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
                sshagent([env.SSH_CRED]) {
                    sh """
                        ssh -o StrictHostKeyChecking=no -p ${env.SSH_PORT} ${env.SSH_USER_NAME}@${env.SSH_REMOTE} "cd public_html && bash ./deploy.sh '${remote}' '${params.BRANCH}'"
                      """
                }
            }
        }
    }

    return branches
}