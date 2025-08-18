/**
* Check if branch is existed
*/
def isBranchExisted(credentialsId, repoUrl, branchName) {
    def branchExists = ""
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASSWORD')]) {
        branchExists = sh(
            script: "git ls-remote --heads ${repoUrl} ${branchName} | wc -l",
            returnStdout: true
            
        ).trim()
    }

    return branchExists != "0"
}

/**
* Check diff branch
*/
def haveDiffBranch(credentialsId, repoName, sourceBranch, destinationBranch) {
    def commitDiff = ""
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASSWORD')]) {
        commitDiff = sh(
            script: """
                gh api repos/${repoName}/compare/${destinationBranch}...${sourceBranch} \
                    --jq '.total_commits'
            """,
            returnStdout: true
        ).trim()
    }

    return commitDiff != "0"
}

/**
* Check if pull request existed, return pr number
*/
def getPullRequest(credentialsId, repoName, sourceBranch, destinationBranch) {
    def prNumber = ""
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASSWORD')]) {
        prNumber = sh(
            script: """
                gh pr list \
                    --repo ${repoName} \
                    --head ${sourceBranch} \
                    --base ${destinationBranch} \
                    --state open \
                    --json number \
                    --jq '.[0].number'
            """,
            returnStdout: true
        ).trim()
    }

    return prNumber
}

/**
* Create new pull request
*/
def createPullRequest(credentialsId, repoName, sourceBranch, destinationBranch) {
    def prNumber = ""
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASSWORD')]) {  
        prNumber = sh(
            script: """
                gh pr create \
                    --repo ${repoName} \
                    --head ${sourceBranch} \
                    --base ${destinationBranch} \
                    --title "Merge ${sourceBranch} into ${destinationBranch}" \
                    --body "Merge ${sourceBranch} into ${destinationBranch}"
            """,
            returnStdout: true
        ).trim()
    }

    return prNumber
}

/**
* Create new pull request
*/
def mergePullRequest(credentialsId, repoName, prNumber) {
    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASSWORD')]) {  
        sh "gh pr merge ${prNumber} --repo ${repoName} --merge --admin"
    }
}

/**
* Create new git branch
*/
def createBranch(credentialsId, repoUrl, branchName, mainBranch) {
    if (isBranchExisted(credentialsId, repoUrl, branchName)) {
        echo "⚠️ Branch '${branchName}' already exists. Skipping."
        return
    }

    deleteDir()

    withCredentials([usernamePassword(credentialsId: credentialsId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
        // Clone main branch and create new branch
        sh """
            git clone --branch ${mainBranch} ${repoUrl} repo
        """

        dir('repo') {
            sh """
                git checkout -b ${branchName}
                git push ${repoUrl} ${branchName}
            """
        }
    }

    deleteDir()
    echo "✅ Created branch: ${branchName} from ${mainBranch}"
}

/**
* Full flow create new pull request github
*/
def createPullRequestFlow(credentialsId, repoUrl, sourceBranch, destinationBranch, autoMerge) {
    echo "Check if sourceBranch branch is existed"
    if (!isBranchExisted(credentialsId, repoUrl, sourceBranch)) {
        return "Branch '${sourceBranch}' does not exist — skipping PR creation."
    }

    echo "Check if destination branch is existed"
    if (!isBranchExisted(credentialsId, repoUrl, destinationBranch)) {
        return "Branch '${destinationBranch}' does not exist — skipping PR creation."
    }

    echo "Branch '${sourceBranch}' and '${destinationBranch}' exists — creating PR."

    def repoName = repoUrl
        .replaceFirst(/^https?:\/\/(?:[^@]+@)?github\.com\//, '')
        .replaceFirst(/\.git$/, '')

    // ✅ Check if branches have differences
    if (!haveDiffBranch(credentialsId, repoName, sourceBranch, destinationBranch)) {
        return "No differences between ${sourceBranch} and ${destinationBranch} — skipping PR creation."
    }

    def prNumber = getPullRequest(credentialsId, repoName, sourceBranch, destinationBranch)
    def prUrl = ""
    if (!prNumber) {
        echo "No existing PR found — creating new PR."
        prNumber = createPullRequest(credentialsId, repoName, sourceBranch, destinationBranch)
        prUrl = prNumber
    } else {
        echo "A PR already exists (#${prNumber})."
        prUrl = "https://github.com/${repoName}/pull/${prNumber}"
    }

    //Auto merge this pull request
    if (autoMerge) {
        echo "Merging PR #${prNumber}..."
        try {
            mergePullRequest(credentialsId, repoName, prNumber)
        } catch (err) {
            prUrl = "⚠️ Auto-merge failed for PR #${prUrl}: ${err.getMessage()}"
        }
    }

    return prUrl
}

def createPullRequestGoLiveFullFlow(repoUrl) {
    def branches = params.FEATURE_BRANCH
        .split(',')
        .collect { it.trim() }
        .findAll { it }

    def prMap = [:]
    def featurePrs = ''
    def featurePr = ''
    def goLivePr = ''

    branches.each { featureBranch ->
        //Create release branch if not existed
        createBranch(env.GITHUB_CREDENTIALS_ID, repoUrl, params.RELEASE_BRANCH, env.MAIN_BRANCH_PWA)

        // Create pull request from feature branch into release branch and auto merge
        featurePrs = createPullRequestFlow(env.GITHUB_CREDENTIALS_ID, repoUrl, featureBranch, params.RELEASE_BRANCH, true)
        featurePrs << "PR merge ${featureBranch} into ${params.RELEASE_BRANCH} : ${featurePr}"

        //Create pull request from release branch into main/master branch
        goLivePr = createPullRequestFlow(env.GITHUB_CREDENTIALS_ID, repoUrl, params.RELEASE_BRANCH, env.MAIN_BRANCH_PWA, false)
    }

    def result = []
    if (goLivePr) {
        result << "PR merge ${params.RELEASE_BRANCH} into ${env.MAIN_BRANCH_PWA} : ${goLivePr}"
    }

    result.addAll(featurePrs)

    return result.join("\n")
}

def createPrForAllRemote(repoUrls) {
    def branches = [:]
    repoUrls.eachWithIndex { repoUrl, index ->
        branches["Create-PR-${index}"] = {
            node {
                def prLines = createPullRequestGoLiveFullFlow(repoUrl)
                // persist this branch’s result so we can aggregate after parallel
                def outFile = "prs-${index}.txt"
                writeFile file: outFile, text: (prLines instanceof List ? prLines.join("\n") : "${prLines}")
                archiveArtifacts artifacts: outFile, fingerprint: true, onlyIfSuccessful: false
            }
        }
    }

    return branches
}