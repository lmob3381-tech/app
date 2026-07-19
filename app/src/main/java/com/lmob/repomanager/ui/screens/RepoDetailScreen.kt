package com.lmob.repomanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmob.repomanager.data.GhContentItem
import com.lmob.repomanager.data.GhRepo
import com.lmob.repomanager.data.GhWorkflowRun

private enum class DetailTab { FILES, ACTIONS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoDetailScreen(
    repo: GhRepo,
    contents: List<GhContentItem>,
    currentPath: String,
    isLoadingContents: Boolean,
    contentsError: String?,
    workflowRuns: List<GhWorkflowRun>,
    isLoadingRuns: Boolean,
    runsError: String?,
    isCleaningRepo: Boolean,
    cleanProgressLog: List<String>,
    cleanDone: Boolean,
    isDeletingRepo: Boolean,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onRefreshRuns: () -> Unit,
    onCleanRepo: () -> Unit,
    onDeleteRepo: () -> Unit
) {
    var tab by remember { mutableStateOf(DetailTab.FILES) }
    var showCleanConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCleanProgress by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(repo.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(repo.owner?.login ?: "", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {

            // Action buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showCleanConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Bersihkan", fontSize = 13.sp)
                }
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Hapus Repo", fontSize = 13.sp)
                }
            }

            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == DetailTab.FILES,
                    onClick = { tab = DetailTab.FILES },
                    text = { Text("File") },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = tab == DetailTab.ACTIONS,
                    onClick = { tab = DetailTab.ACTIONS; onRefreshRuns() },
                    text = { Text("Actions") },
                    icon = { Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (tab) {
                DetailTab.FILES -> FilesTab(
                    contents = contents,
                    currentPath = currentPath,
                    isLoading = isLoadingContents,
                    error = contentsError,
                    onNavigate = onNavigate
                )
                DetailTab.ACTIONS -> ActionsTab(
                    runs = workflowRuns,
                    isLoading = isLoadingRuns,
                    error = runsError,
                    onRefresh = onRefreshRuns
                )
            }
        }
    }

    if (showCleanConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanConfirm = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary) },
            title = { Text("Kosongkan repo?") },
            text = { Text("Semua file di '${repo.name}' akan dihapus permanen dan langsung ter-push ke GitHub. Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    showCleanConfirm = false
                    showCleanProgress = true
                    onCleanRepo()
                }) { Text("Ya, kosongkan", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCleanConfirm = false }) { Text("Batal") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Hapus repo permanen?") },
            text = { Text("Repo '${repo.name}' akan dihapus selamanya dari GitHub, termasuk semua riwayat, issue, dan Actions. Tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteRepo()
                }) { Text("Ya, hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Batal") }
            }
        )
    }

    if (showCleanProgress) {
        AlertDialog(
            onDismissRequest = { if (!isCleaningRepo) showCleanProgress = false },
            title = { Text(if (isCleaningRepo) "Membersihkan repo..." else "Selesai") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    LazyColumn {
                        items(cleanProgressLog) { line ->
                            Text(line, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                    if (isCleaningRepo) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                if (!isCleaningRepo) {
                    TextButton(onClick = { showCleanProgress = false }) { Text("Tutup") }
                }
            }
        )
    }

    if (isDeletingRepo) {
        Dialog(onDismissRequest = {}) {
            Card {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Menghapus repo...")
                }
            }
        }
    }
}

@Composable
private fun Dialog(onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) { content() }
}

@Composable
private fun FilesTab(
    contents: List<GhContentItem>,
    currentPath: String,
    isLoading: Boolean,
    error: String?,
    onNavigate: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // breadcrumb
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (currentPath.isEmpty()) "/" else "/$currentPath",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            contents.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("📭 Folder kosong", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                if (currentPath.isNotEmpty()) {
                    item {
                        ListItem(
                            headlineContent = { Text("..") },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                            modifier = Modifier.clickable {
                                val parent = currentPath.substringBeforeLast("/", "")
                                onNavigate(parent)
                            }
                        )
                    }
                }
                items(contents) { item ->
                    ListItem(
                        headlineContent = { Text(item.name, fontSize = 14.sp) },
                        supportingContent = if (item.type == "file" && item.size != null) {
                            { Text(formatSize(item.size), fontSize = 11.sp) }
                        } else null,
                        leadingContent = {
                            Icon(
                                if (item.type == "dir") Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (item.type == "dir") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable(enabled = item.type == "dir") {
                            if (item.type == "dir") onNavigate(item.path)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsTab(
    runs: List<GhWorkflowRun>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Riwayat run terakhir", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            runs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Belum ada riwayat Actions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(runs) { run -> WorkflowRunCard(run) }
            }
        }
    }
}

@Composable
private fun WorkflowRunCard(run: GhWorkflowRun) {
    val (color, icon, label) = statusVisuals(run.status, run.conclusion)
    Card(shape = RoundedCornerShape(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(run.displayTitle ?: run.name ?: "Workflow run", fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text("${run.headBranch ?: "?"} · $label", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
        }
    }
}

private fun statusVisuals(status: String?, conclusion: String?): Triple<Color, androidx.compose.ui.graphics.vector.ImageVector, String> {
    return when {
        status == "in_progress" || status == "queued" -> Triple(Color(0xFFD29922), Icons.Default.HourglassTop, "Berjalan")
        conclusion == "success" -> Triple(Color(0xFF3FB950), Icons.Default.CheckCircle, "Sukses")
        conclusion == "failure" -> Triple(Color(0xFFF85149), Icons.Default.Cancel, "Gagal")
        conclusion == "cancelled" -> Triple(Color(0xFF8B949E), Icons.Default.Block, "Dibatalkan")
        else -> Triple(Color(0xFF8B949E), Icons.Default.HelpOutline, status ?: "Tidak diketahui")
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}
