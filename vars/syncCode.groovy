import org.xuxi.utils.GitUtils
import org.xuxi.utils.NotifyUtils

def call() {
    def timestamp = new Date().format("yyyyMMdd-HHmmss")
    def tempFolder = "temp-folder-${timestamp}"

    dir(env.WORK_DIR) {
        //Clone the source repository
        echo "Cloning branch '${params.BRANCH}' from '${env.SOURCE_REPO_URL}' into '${tempFolder}'..."
        GitUtils.cloneGit(this, env.SOURCE_CREDENTIAL_ID, env.SOURCE_REPO_URL, params.BRANCH, tempFolder)

        dir(tempFolder) {
            // Pull the latest code from the destination repository
            echo "Pulling latest code from '${env.DESTINATION_REPO_URL}' branch '${params.BRANCH}'..."
            GitUtils.pullCode(this, env.DESTINATION_CREDENTIAL_ID, env.DESTINATION_REPO_URL, params.BRANCH)

            // Push the code to the destination repository
            echo "Pushing code to '${env.DESTINATION_REPO_URL}' branch '${params.BRANCH}'..."
            GitUtils.pushCode(this, env.DESTINATION_CREDENTIAL_ID, env.DESTINATION_REPO_URL, params.BRANCH)
        }

        // Cleanup
        sh "rm -rf ${tempFolder}"
        echo "Temporary folder '${tempFolder}' removed."
    }
}