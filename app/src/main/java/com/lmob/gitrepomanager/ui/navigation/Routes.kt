package com.lmob.gitrepomanager.ui.navigation

/**
 * Central definition of every navigation destination in the app.
 * Step 2 must use these exact route strings/args when wiring up
 * RepoDetailScreen, FileBrowserScreen, ActionsScreen, SettingsScreen,
 * and DangerZoneScreen — do not invent new route patterns.
 */
object Routes {
    const val LOGIN = "login"
    const val REPO_LIST = "repo_list"

    // Args: ownerLogin, repoName
    const val REPO_DETAIL = "repo_detail/{owner}/{repo}"
    fun repoDetail(owner: String, repo: String) = "repo_detail/$owner/$repo"

    // Args: ownerLogin, repoName, path (URL-encoded, may be empty for root)
    const val FILE_BROWSER = "file_browser/{owner}/{repo}/{path}"
    fun fileBrowser(owner: String, repo: String, path: String = ""): String {
        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
        return "file_browser/$owner/$repo/$encodedPath"
    }

    const val ACTIONS = "actions/{owner}/{repo}"
    fun actions(owner: String, repo: String) = "actions/$owner/$repo"

    const val DANGER_ZONE = "danger_zone/{owner}/{repo}/{branch}"
    fun dangerZone(owner: String, repo: String, branch: String) =
        "danger_zone/$owner/$repo/$branch"

    const val SETTINGS = "settings"

    // Argument keys
    const val ARG_OWNER = "owner"
    const val ARG_REPO = "repo"
    const val ARG_PATH = "path"
    const val ARG_BRANCH = "branch"
}
