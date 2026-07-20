package com.lmob.gitrepomanager.ui.screens.repodetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lmob.gitrepomanager.ui.components.FullScreenError
import com.lmob.gitrepomanager.ui.components.FullScreenLoading
import com.lmob.gitrepomanager.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    onBack: () -> Unit,
    onOpenFileBrowser: (owner: String, repo: String, path: String) -> Unit,
    onOpenActions: (owner: String, repo: String) -> Unit,
    onOpenDangerZone: (owner: String, repo: String, branch: String) -> Unit,
    viewModel: RepoDetailViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.repoName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> FullScreenLoading(
                modifier = Modifier.padding(padding),
                label = "Memuat repository…"
            )
            state.errorMessage != null && state.repo == null -> FullScreenError(
                message = state.errorMessage!!,
                modifier = Modifier.padding(padding),
                onRetry = viewModel::loadRepo
            )
            state.repo != null -> {
                val repo = state.repo!!
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                    TabRow(selectedTabIndex = state.selectedTab.ordinal) {
                        Tab(
                            selected = state.selectedTab == RepoDetailTab.FILES,
                            onClick = { viewModel.onTabSelected(RepoDetailTab.FILES) },
                            text = { Text("Berkas") }
                        )
                        Tab(
                            selected = state.selectedTab == RepoDetailTab.ACTIONS,
                            onClick = { viewModel.onTabSelected(RepoDetailTab.ACTIONS) },
                            text = { Text("Actions") }
                        )
                        Tab(
                            selected = state.selectedTab == RepoDetailTab.ABOUT,
                            onClick = { viewModel.onTabSelected(RepoDetailTab.ABOUT) },
                            text = { Text("Tentang") }
                        )
                    }

                    when (state.selectedTab) {
                        RepoDetailTab.FILES -> FilesTabPlaceholder(
                            onOpenFileBrowser = {
                                onOpenFileBrowser(repo.owner.login, repo.name, "")
                            }
                        )
                        RepoDetailTab.ACTIONS -> ActionsTabPlaceholder(
                            onOpenActions = { onOpenActions(repo.owner.login, repo.name) }
                        )
                        RepoDetailTab.ABOUT -> AboutTab(
                            repo = repo,
                            onOpenDangerZone = {
                                onOpenDangerZone(repo.owner.login, repo.name, repo.defaultBranch)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilesTabPlaceholder(onOpenFileBrowser: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Folder,
            contentDescription = null,
            modifier = Modifier.height(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Jelajahi berkas & folder repository ini", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onOpenFileBrowser) {
            Text("Buka Berkas")
        }
    }
}

@Composable
private fun ActionsTabPlaceholder(onOpenActions: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.PlayCircle,
            contentDescription = null,
            modifier = Modifier.height(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("Lihat riwayat & status GitHub Actions", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.Button(onClick = onOpenActions) {
            Text("Buka Actions")
        }
    }
}

@Composable
private fun AboutTab(
    repo: com.lmob.gitrepomanager.data.model.GitHubRepo,
    onOpenDangerZone: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(repo.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!repo.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(repo.description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            text = if (repo.private) "Privat" else "Publik",
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                        if (repo.archived) {
                            StatusPill(
                                text = "Diarsipkan",
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    InfoRow("Bahasa utama", repo.language ?: "-")
                    InfoRow("Bintang", repo.stargazersCount.toString())
                    InfoRow("Fork", repo.forksCount.toString())
                    InfoRow("Issue terbuka", repo.openIssuesCount.toString())
                    InfoRow("Branch utama", repo.defaultBranch)
                    InfoRow("Diperbarui", repo.updatedAt)
                }
            }
        }

        item {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Zona Berbahaya",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Mengosongkan repository akan menghapus semua berkas secara permanen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    com.lmob.gitrepomanager.ui.components.DangerOutlinedButton(
                        text = "Buka Zona Berbahaya",
                        onClick = onOpenDangerZone,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
