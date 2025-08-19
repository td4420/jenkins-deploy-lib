package org.xuxi.utils

class NotifyUtils {
    static void sendNotification(script, String message) {
        script.echo "📢 Sending notification:\n ${message}"
    }

    static void notifyCreatePrGoLive(script, List<String> remotes) {
        // pull all stashed files into THIS workspace
        for (int i = 0; i < remotes.size(); i++) {
            script.unstash "prs-${remotes[i]}"
        }

        def msgLines = []
        // Use index-based loop to avoid capturing a non-serializable iterator
        for (int i = 0; i < remotes.size(); i++) {
            def remote = remotes[i]
            def file = "prs-${remote}.txt"
            msgLines << "**PRs created for ${remote}**".toUpperCase()
            if (fileExists(file)) {
                msgLines << "${script.readFile(file).trim()}"
            }
        }

        sendNotification(script, msgLines.join("\n"))
    }
}