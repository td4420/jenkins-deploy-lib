import org.xuxi.utils.GitUtils
import org.xuxi.utils.NotifyUtils

def call() {
    def repoUrls = (env.REPO_URLS ?: "")
        .split("###")
        .findAll { it }

    def remotes = (params.REMOTES ?: '')
        .split(',')
        .collect { it.trim() }
        .findAll { it }

    def processors = generateProcessor(repoUrls, remotes)

    //Run processors in parallel
    parallel processors
    NotifyUtils.notifyCreatePrGoLive(this, remotes)
}

def createPullRequestGoLiveFullFlow(repoUrl, mainBranch) {
    def branches = params.FEATURE_BRANCH
        .split(',')
        .collect { it.trim() }
        .findAll { it }

    def featurePrs = []
    def featurePr = ''
    def goLivePr = ''

    //Create release branch if not existed
    def repoName = repoUrl
        .replaceFirst(/^https?:\/\/(?:[^@]+@)?github\.com\//, '')
        .replaceFirst(/\.git$/, '')
    GitUtils.createBranch(this, repoName, mainBranch, params.RELEASE_BRANCH)

    branches.each { featureBranch ->
        // Create pull request from feature branch into release branch and auto merge
        featurePr = GitUtils.createPullRequestFlow(this, repoUrl, featureBranch, params.RELEASE_BRANCH, true)
        featurePrs << "- PR merge ${featureBranch} into ${params.RELEASE_BRANCH} : ${featurePr}"
    }

    //Create pull request from release branch into main/master branch
    goLivePr = GitUtils.createPullRequestFlow(this, repoUrl, params.RELEASE_BRANCH, mainBranch, false)

    def result = []
    if (goLivePr) {
        result << "- PR merge ${params.RELEASE_BRANCH} into ${mainBranch} : ${goLivePr}"
    }

    result.addAll(featurePrs)

    return result.join("\n")
}

def generateProcessor(repoUrls, remotes) {
    def branches = [:]
    repoUrls.eachWithIndex { repoUrl, index ->
        branches["Create-PR-${remotes[index]}"] = {
            node {
                def mainBranch = (remote == 'm2') ? env.MAIN_BRANCH_M2 : env.MAIN_BRANCH_PWA
                def prLines = createPullRequestGoLiveFullFlow(repoUrl, mainBranch)
                // persist this branch’s result so we can aggregate after parallel
                def outFile = "prs-${remotes[index]}.txt"
                writeFile file: outFile, text: (prLines instanceof List ? prLines.join("\n") : "${prLines}")
                stash name: "prs-${remotes[index]}", includes: outFile, useDefaultExcludes: false
            }
        }
    }

    return branches
}