package com.lmob.gitrepomanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lmob.gitrepomanager.data.repository.GitHubRepository
import com.lmob.gitrepomanager.ui.navigation.GitRepoManagerNavGraph
import com.lmob.gitrepomanager.ui.theme.GitRepoManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: GitHubRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() and before setContentView/setContent
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            GitRepoManagerTheme {
                GitRepoManagerNavGraph(
                    startDestinationIsLoggedIn = repository.isLoggedIn()
                )
            }
        }
    }
}
