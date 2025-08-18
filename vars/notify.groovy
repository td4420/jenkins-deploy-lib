def call(String message) {
    echo "📢 Sending notification: ${message}"
}

def notifyCreatePrGoLive(remotes) {
    // pull all stashed files into THIS workspace
    for (int i = 0; i < remotes.size(); i++) {
        unstash "prs-${remotes[i]}"
    }

    def msgLines = []
    // Use index-based loop to avoid capturing a non-serializable iterator
    for (int i = 0; i < remotes.size(); i++) {
        def remote = remotes[i]
        def file = "prs-${remote}.txt"
        msgLines << "**PRs created for ${remote} PWA**".toUpperCase()
        if (fileExists(file)) {
            msgLines << "- ${readFile(file).trim()}"
        }
    }

    notify(msgLines.join("\n"))
}