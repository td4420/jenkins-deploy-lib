def call() {
    def branches = generateProcessor()

    parallel branches
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
                    wait: false,
                    propagate: true
                )
                    
                def jobName   = downstream.getProjectName()
                def buildNum  = downstream.number
                def jenkinsUrl = env.JENKINS_URL

                def blueUrl = "${jenkinsUrl}blue/organizations/jenkins/${jobName}/detail/${jobName}/${buildNum}/pipeline"

                echo "🌐 Blue Ocean link: ${blueUrl}"
            }
        }
    }

    return branches
}