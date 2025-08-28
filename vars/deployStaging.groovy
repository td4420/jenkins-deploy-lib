def call() {
    def downstreamJobs = [:]
    def branches = generateProcessor(downstreamJobs)

    parallel branches

    stage('Collect Downstream Links') {
        script {
            downstreamJobs.each { remote, future ->
                // Wait until downstream is finished, then build link
                def run = future.get()
                def jobName = run.parent.fullName
                def buildNum = run.number
                def blueUrl = "${env.JENKINS_URL}blue/organizations/jenkins/${jobName}/detail/${jobName}/${buildNum}/pipeline"

                echo "🌐 ${remote} → ${blueUrl}"
            }
        }
    }
}

def generateProcessor(downstreamJobs) {
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

                downstreamJobs[remote] = downstream
            }
        }
    }

    return branches
}