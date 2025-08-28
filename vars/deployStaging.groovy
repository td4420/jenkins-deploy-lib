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

                echo "Result: ${downstream.getResult()}" 
                def jobName   = downstream.getProjectName()
                def buildNum  = downstream.number
                def jenkinsUrl = env.JENKINS_URL

                def blueUrl = "${jenkinsUrl}blue/organizations/jenkins/${jobName}/detail/${jobName}/${buildNum}/pipeline"

                // get log lines (List<String>)
                def logLines = downstream.rawBuild.getLog(1000)  // last 1000 lines
                logLines.each { line ->
                    echo line
                }

                NotifyUtils.sendNotification(this, "🌐 Blue Ocean link: ${blueUrl}")
            }
        }
    }

    return branches
}