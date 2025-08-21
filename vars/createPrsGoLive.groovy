import org.xuxi.utils.GitUtils
import org.xuxi.utils.NotifyUtils

def call() {
    def repoUrls = (env.PWA_REPO_URLS ?: "")
        .split("###")
        .findAll { it }

    def remotes = (params.REMOTES ?: '')
        .split(',')
        .collect { it.trim() }
        .findAll { it }

    def pwaProcessors = generateProcessor(repoUrls, remotes)
    def m2Processors = generateProcessor([env.M2_REPO_URL], ['M2'])

    //Run the PWA processors in parallel
    parallel pwaProcessors
    NotifyUtils.notifyCreatePrGoLive(this, remotes)

    //Run the M2 processor in parallel
    // parallel m2Processors
    // NotifyUtils.notifyCreatePrGoLive(this, ['M2'])
}

def createPullRequestGoLiveFullFlow(repoUrl) {
    def branches = params.FEATURE_BRANCH
        .split(',')
        .collect { it.trim() }
        .findAll { it }

    def featurePrs = []
    def featurePr = ''
    def goLivePr = ''

    //Create release branch if not existed
    GitUtils.createBranch(this, env.GITHUB_CREDENTIALS_ID, repoUrl, params.RELEASE_BRANCH, env.MAIN_BRANCH_PWA)

    branches.each { featureBranch ->
        // Create pull request from feature branch into release branch and auto merge
        featurePr = GitUtils.createPullRequestFlow(this, env.GITHUB_CREDENTIALS_ID, repoUrl, featureBranch, params.RELEASE_BRANCH, true)
        featurePrs << "- PR merge ${featureBranch} into ${params.RELEASE_BRANCH} : ${featurePr}"
    }

    //Create pull request from release branch into main/master branch
    goLivePr = GitUtils.createPullRequestFlow(this, env.GITHUB_CREDENTIALS_ID, repoUrl, params.RELEASE_BRANCH, env.MAIN_BRANCH_PWA, false)

    def result = []
    if (goLivePr) {
        result << "- PR merge ${params.RELEASE_BRANCH} into ${env.MAIN_BRANCH_PWA} : ${goLivePr}"
    }

    result.addAll(featurePrs)

    return result.join("\n")
}

def generateProcessor(repoUrls, remotes) {
    def branches = [:]
    repoUrls.eachWithIndex { repoUrl, index ->
        branches["Create-PR-${remotes[index]}"] = {
            node {
                def prLines = createPullRequestGoLiveFullFlow(repoUrl)
                // persist this branch’s result so we can aggregate after parallel
                def outFile = "prs-${remotes[index]}.txt"
                writeFile file: outFile, text: (prLines instanceof List ? prLines.join("\n") : "${prLines}")
                stash name: "prs-${remotes[index]}", includes: outFile, useDefaultExcludes: false
            }
        }
    }

    return branches
}