package org.xuxi.utils
import groovy.json.JsonOutput

class NotifyUtils {
    static void sendNotification(script, String message, int status, String link = null) {
        def payload = [
            project: script.env.PROJECT_NAME,
            message: message,
            status : 0
        ]

        if (link) {
            payload.link = link
        }

        def json = JsonOutput.toJson(payload)

        script.sh """
            curl --header "Content-Type: application/json" \
                --request POST \
                --data '${json}' \
                https://cicd.bssdev.cloud/post
        """
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
            if (script.fileExists(file)) {
                msgLines << "${script.readFile(file).trim()}"
            }
        }

        sendNotification(script, msgLines.join("\n"), 0, null)
    }
}