import groovy.json.JsonSlurper
import groovy.json.JsonOutput

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
            echo "⚠️ Auto-merge failed for PR #${prNumber}: ${err.getMessage()}"
        }
    }

    return prUrl
}

def createPullRequestGoLiveFullFlow(featureBranches, credentialsId, repoUrl, releaseBranch, mainBranch) {
    def branches = params.FEATURE_BRANCH
        .split(',')
        .collect { it.trim() }
        .findAll { it }

    def prMap = [:]
    def featurePr = ''
    def goLivePr = ''

    branches.each { featureBranch ->
        //Create release branch if not existed
        createBranch(credentialsId, repoUrl, releaseBranch, mainBranch)

        // Create pull request from feature branch into release branch and auto merge
        featurePr = createPullRequestFlow(credentialsId, repoUrl, featureBranch, releaseBranch, true)
        prMap["PR merge ${featureBranch} into ${releaseBranch}"] = featurePr

        //Create pull request from release branch into main/master branch
        goLivePr = createPullRequestFlow(credentialsId, repoUrl, releaseBranch, mainBranch, false)
        prMap["PR merge ${releaseBranch} into ${mainBranch}"] = goLivePr
    }

    return prMap.collect { k, v -> "${k} : ${v}" }.join("\n");
}