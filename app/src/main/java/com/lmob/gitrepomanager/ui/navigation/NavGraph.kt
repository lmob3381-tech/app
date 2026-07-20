package com.lmob.gitrepomanager.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lmob.gitrepomanager.ui.screens.actions.ActionsScreen
import com.lmob.gitrepomanager.ui.screens.dangerzone.DangerZoneScreen
import com.lmob.gitrepomanager.ui.screens.filebrowser.FileBrowserScreen
import com.lmob.gitrepomanager.ui.screens.login.LoginScreen
import com.lmob.gitrepomanager.ui.screens.repodetail.RepoDetailScreen
import com.lmob.gitrepomanager.ui.screens.repolist.RepoListScreen
import com.lmob.gitrepomanager.ui.screens.settings.SettingsScreen

/**
 * Single navigation graph for the whole app. Route strings and argument
 * keys are defined centrally in [Routes] — do not hardcode route strings
 * anywhere else.
 */
@Composable
fun GitRepoManagerNavGraph(startDestinationIsLoggedIn: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (startDestinationIsLoggedIn) Routes.REPO_LIST else Routes.LOGIN

    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.REPO_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.REPO_LIST) {
            RepoListScreen(
                onRepoClick = { owner, repo ->
                    navController.navigate(Routes.repoDetail(owner, repo))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            route = Routes.REPO_DETAIL,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_REPO) { type = NavType.StringType }
            )
        ) {
            RepoDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenFileBrowser = { owner, repo, path ->
                    navController.navigate(Routes.fileBrowser(owner, repo, path))
                },
                onOpenActions = { owner, repo ->
                    navController.navigate(Routes.actions(owner, repo))
                },
                onOpenDangerZone = { owner, repo, branch ->
                    navController.navigate(Routes.dangerZone(owner, repo, branch))
                }
            )
        }

        composable(
            route = Routes.FILE_BROWSER,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_REPO) { type = NavType.StringType },
                navArgument(Routes.ARG_PATH) {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            FileBrowserScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.ACTIONS,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_REPO) { type = NavType.StringType }
            )
        ) {
            ActionsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.DANGER_ZONE,
            arguments = listOf(
                navArgument(Routes.ARG_OWNER) { type = NavType.StringType },
                navArgument(Routes.ARG_REPO) { type = NavType.StringType },
                navArgument(Routes.ARG_BRANCH) { type = NavType.StringType }
            )
        ) {
            DangerZoneScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
