package org.xuxi.utils

class GitUtils {
    /**
    * Check if branch exists using GitHub CLI
    */
    static boolean isBranchExisted(script, String repoOwner, String repoName, String branchName) {
        def status = script.sh(
            script: """
                gh api repos/${repoOwner}/${repoName}/branches/${branchName} --silent >/dev/null 2>&1
            """,
            returnStatus: true
        )

        return status == 0
    }

    /**
     * Check if branches have differences
     */
    static boolean haveDiffBranch(script, String credentialsId, String repoName, String sourceBranch, String destinationBranch) {
        def commitDiff = ""
        script.withCredentials([script.usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'GIT_USER',
            passwordVariable: 'GIT_PASSWORD'
        )]) {
            commitDiff = script.sh(
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
     * Check if pull request exists, return PR number
     */
    static String getPullRequest(script, String credentialsId, String repoName, String sourceBranch, String destinationBranch) {
        def prNumber = ""
        script.withCredentials([script.usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'GIT_USER',
            passwordVariable: 'GIT_PASSWORD'
        )]) {
            prNumber = script.sh(
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
    static String createPullRequest(script, String credentialsId, String repoName, String sourceBranch, String destinationBranch) {
        def prNumber = ""
        script.withCredentials([script.usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'GIT_USER',
            passwordVariable: 'GIT_PASSWORD'
        )]) {
            prNumber = script.sh(
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
     * Merge pull request
     */
    static void mergePullRequest(script, String credentialsId, String repoName, String prNumber) {
        script.withCredentials([script.usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'GIT_USER',
            passwordVariable: 'GIT_PASSWORD'
        )]) {
            script.sh "gh pr merge ${prNumber} --repo ${repoName} --merge --admin"
        }
    }

    /**
    * Create new branch using GitHub CLI instead of raw git + credentials
    */
    static void createBranch(script, String repoName, String mainBranch, String newBranch) {
        // Get the latest commit SHA of the main branch
        def sha = script.sh(
            script: """
                gh api repos/${repoName}/git/ref/heads/${mainBranch} --jq '.object.sha'
            """,
            returnStdout: true
        ).trim()

        // Create the new branch ref pointing to that commit
        def status = script.sh(
            script: """
                gh api repos/${repoOwner}/${repoName}/git/refs \\
                -f ref=refs/heads/${newBranch} \\
                -f sha=${sha}
            """,
            returnStatus: true
        )

        if (status == 0) {
            script.echo "✅ Created branch '${newBranch}' from '${mainBranch}'"
        } else {
            script.echo "⚠️ Failed to create branch '${newBranch}' (maybe it already exists)"
        }
    }

    /**
     * Full flow create new pull request github
     */
    static String createPullRequestFlow(script, String credentialsId, String repoUrl, String sourceBranch, String destinationBranch, boolean autoMerge) {
        script.echo "Check if sourceBranch branch exists"
        if (!isBranchExisted(script, credentialsId, repoUrl, sourceBranch)) {
            return "Branch '${sourceBranch}' does not exist — skipping PR creation."
        }

        script.echo "Check if destination branch exists"
        if (!isBranchExisted(script, credentialsId, repoUrl, destinationBranch)) {
            return "Branch '${destinationBranch}' does not exist — skipping PR creation."
        }

        script.echo "Branch '${sourceBranch}' and '${destinationBranch}' exists — creating PR."

        def repoName = repoUrl
            .replaceFirst(/^https?:\/\/(?:[^@]+@)?github\.com\//, '')
            .replaceFirst(/\.git$/, '')

        // ✅ Check if branches have differences
        if (!haveDiffBranch(script, credentialsId, repoName, sourceBranch, destinationBranch)) {
            return "No differences between ${sourceBranch} and ${destinationBranch} — skipping PR creation."
        }

        def prNumber = getPullRequest(script, credentialsId, repoName, sourceBranch, destinationBranch)
        def prUrl = ""
        if (!prNumber) {
            script.echo "No existing PR found — creating new PR."
            prNumber = createPullRequest(script, credentialsId, repoName, sourceBranch, destinationBranch)
            prUrl = prNumber
        } else {
            script.echo "A PR already exists (#${prNumber})."
            prUrl = "https://github.com/${repoName}/pull/${prNumber}"
        }

        // Auto merge this pull request
        if (autoMerge) {
            script.echo "Merging PR #${prNumber}..."
            try {
                mergePullRequest(script, credentialsId, repoName, prNumber)
            } catch (err) {
                prUrl = "⚠️ Auto-merge failed for PR #${prUrl}: ${err.getMessage()}"
            }
        }

        return prUrl
    }
}
