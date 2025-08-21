package org.xuxi.utils

class GitUtils {
    /**
     * Pull code from git repository
     */
    static void pullCode(script, String credentialsId, String repoUrl, String branchName) {
        if (!isBranchExisted(script, credentialsId, repoUrl, branchName)) {
            script.echo "⚠️ Branch '${branchName}' does not exist in the repository '${repoUrl}'. Skipping pull."
            return
        }

        script.withCredentials([script.usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'GIT_USER',
            passwordVariable: 'GIT_PASSWORD'
        )]) {
            def GIT_CREDENTIALS = "$script.env.GIT_USER:$script.env.GIT_PASSWORD"
            def authedRepoUrl = repoUrl.replaceFirst(
                /^https:\/\//,
                "https://${GIT_CREDENTIALS}@"
            )

            script.sh """
                set +x
                git pull ${authedRepoUrl} ${branchName}
            """
        }
    }

    /**
     * Push code to git repository
     */
    static void pushCode(script, String credentialsId, String repoUrl, String branchName) {
        script.withCredentials([script.usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'GIT_USER',
            passwordVariable: 'GIT_PASSWORD'
        )]) {
            def GIT_CREDENTIALS = "$script.env.GIT_USER:$script.env.GIT_PASSWORD"
            def authedRepoUrl = repoUrl.replaceFirst(
                /^https:\/\//,
                "https://${GIT_CREDENTIALS}@"
            )

            script.sh """
                set +x
                git push ${authedRepoUrl} ${branchName}
            """
        }
    }

    /**
    * Clone git repository
    */
    static boolean cloneGit(script, String credentialsId, String repoUrl, String branchName, String folderName) {
        if (!isBranchExisted(script, credentialsId, repoUrl, branchName)) {
            script.echo "⚠️ Branch '${branchName}' is not exists."
            return false
        }

        try {
            script.withCredentials([script.usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'GIT_USER',
                passwordVariable: 'GIT_PASSWORD'
            )]) {
                def GIT_CREDENTIALS = "$script.env.GIT_USER:$script.env.GIT_PASSWORD"
                def authedRepoUrl = repoUrl.replaceFirst(
                    /^https:\/\//,
                    "https://${GIT_CREDENTIALS}@"
                )

                script.sh """
                    set +x
                    git clone --branch ${branchName} ${authedRepoUrl} ${folderName}
                """
            }
        } catch (err) {
            script.echo "❌ Failed to clone repository: ${err.getMessage()}"
            return false
        }
    }

    /**
     * Check if branch exists
     */
    static boolean isBranchExisted(script, String credentialsId, String repoUrl, String branchName) {
        def branchExists = ""
        script.withCredentials([script.usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'GIT_USER',
            passwordVariable: 'GIT_PASSWORD'
        )]) {
            def GIT_CREDENTIALS = "$script.env.GIT_USER:$script.env.GIT_PASSWORD"
            def authedRepoUrl = repoUrl.replaceFirst(
                /^https:\/\//,
                "https://${GIT_CREDENTIALS}@"
            )

            branchExists = script.sh(
                script: """
                    set +x
                    git ls-remote --heads ${authedRepoUrl} ${branchName} | wc -l
                """,
                returnStdout: true
            ).trim()
        }
        return branchExists != "0"
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
     * Create new git branch
     */
    static void createBranch(script, String credentialsId, String repoUrl, String branchName, String mainBranch) {
        if (isBranchExisted(script, credentialsId, repoUrl, branchName)) {
            script.echo "⚠️ Branch '${branchName}' already exists. Skipping."
            return
        }

        script.deleteDir()

        script.withCredentials([script.usernamePassword(
            credentialsId: credentialsId,
            usernameVariable: 'GIT_USER',
            passwordVariable: 'GIT_PASS'
        )]) {
            // Clone main branch and create new branch
            script.sh """
                git clone --branch ${mainBranch} ${repoUrl} repo
            """

            script.dir('repo') {
                script.sh """
                    git checkout -b ${branchName}
                    git push ${repoUrl} ${branchName}
                """
            }
        }

        script.deleteDir()
        script.echo "✅ Created branch: ${branchName} from ${mainBranch}"
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
