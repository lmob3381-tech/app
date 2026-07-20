package com.lmob.gitrepomanager.ui.screens.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.lmob.gitrepomanager.data.model.GitHubWorkflowRun
import com.lmob.gitrepomanager.ui.components.FullScreenError
import com.lmob.gitrepomanager.ui.components.FullScreenLoading
import com.lmob.gitrepomanager.ui.theme.LocalStatusColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionsScreen(
    onBack: () -> Unit,
    viewModel: ActionsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GitHub Actions", fontWeight = FontWeight.Bold) },
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
                label = "Memuat riwayat Actions…"
            )
            state.errorMessage != null && state.runs.isEmpty() -> FullScreenError(
                message = state.errorMessage!!,
                modifier = Modifier.padding(padding),
                onRetry = { viewModel.loadRuns(isInitial = true) }
            )
            state.runs.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Belum ada riwayat Actions untuk repository ini",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = state.isRefreshing),
                onRefresh = viewModel::onRefresh,
                modifier = Modifier.padding(padding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.runs, key = { it.id }) { run ->
                        WorkflowRunCard(run = run)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkflowRunCard(run: GitHubWorkflowRun) {
    val statusColors = LocalStatusColors.current
    val (icon, tint) = statusIconAndColor(run, statusColors.success, statusColors.danger, statusColors.warning)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = run.displayTitle ?: run.name ?: "Run #${run.runNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${run.headBranch ?: "-"} · ${run.event} · #${run.runNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun statusIconAndColor(
    run: GitHubWorkflowRun,
    success: Color,
    danger: Color,
    warning: Color
): Pair<androidx.compose.ui.graphics.vector.ImageVector, Color> {
    if (run.status != "completed") {
        return Icons.Filled.HourglassEmpty to warning
    }
    return when (run.conclusion) {
        "success" -> Icons.Filled.CheckCircle to success
        "failure" -> Icons.Filled.Error to danger
        "cancelled" -> Icons.Filled.RemoveCircle to warning
        else -> Icons.Filled.HourglassEmpty to warning
    }
}
