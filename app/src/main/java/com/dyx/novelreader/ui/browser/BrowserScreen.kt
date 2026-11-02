package com.dyx.novelreader.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyx.novelreader.data.model.RemoteEntry
import com.dyx.novelreader.data.model.RemoteProtocol
import com.dyx.novelreader.data.model.RemoteServer
import com.dyx.novelreader.viewmodel.BrowserViewModel

/**
 * 书库浏览层：服务器列表 ↔ 目录浏览。
 *
 * 打开一本书后交给上层（加入书架 + 进入阅读页）。
 */
@Composable
fun BrowserScreen(
    onOpenEntry: (RemoteEntry, RemoteServer) -> Unit,
    onExit: () -> Unit,
) {
    val vm: BrowserViewModel = viewModel()
    val servers by vm.servers.collectAsState()
    val browse by vm.browse.collectAsState()
    val discovered by vm.discovered.collectAsState()
    val discovering by vm.discovering.collectAsState()
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<RemoteServer?>(null) }
    var prefillHost by remember { mutableStateOf<String?>(null) }
    var prefillProtocol by remember { mutableStateOf(RemoteProtocol.SMB) }

    // 返回键：目录内逐级返回，回到服务器列表后再返回则回书架
    BackHandler {
        if (browse.server != null) vm.goUp() else onExit()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = browse.server,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(220))) togetherWith
                            fadeOut(tween(140))
                } else {
                    fadeIn(tween(220)) togetherWith
                            (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(160)))
                }
            },
            label = "browse",
        ) { server ->
            if (server == null) {
                ServerListScreen(
                    servers = servers,
                    discovered = discovered,
                    discovering = discovering,
                    onOpenServer = { vm.connect(it) },
                    onAddServer = {
                        prefillHost = null
                        prefillProtocol = RemoteProtocol.SMB
                        showAddDialog = true
                    },
                    onEditServer = { editingServer = it },
                    onDeleteServer = { vm.deleteServer(it.id) },
                    onToggleScan = {
                        if (discovering) vm.stopDiscovery() else vm.startDiscovery(context)
                    },
                    onPickDiscovered = { service ->
                        prefillHost = if (service.port != 0 && service.port != 445) {
                            "${service.host}:${service.port}"
                        } else {
                            service.host
                        }
                        prefillProtocol = service.protocol
                        showAddDialog = true
                    },
                    onBackToShelf = onExit,
                )
            } else {
                DirListScreen(
                    server = server,
                    path = browse.path,
                    entries = browse.entries,
                    loading = browse.loading,
                    error = browse.error,
                    onBack = { vm.goUp() },
                    onOpenDir = { vm.openDir(it) },
                    onOpenEntry = { entry -> onOpenEntry(entry, server) },
                    onExitToList = { vm.exitBrowse() },
                    onRetry = { vm.retry() },
                )
            }
        }
    }

    val dialogOpen = showAddDialog || editingServer != null
    if (dialogOpen) {
        ServerDialog(
            initial = editingServer,
            prefillHost = prefillHost,
            prefillProtocol = prefillProtocol,
            onTest = { server, onResult -> vm.testConnection(server, onResult) },
            onSave = { server ->
                vm.saveServer(server)
                showAddDialog = false
                editingServer = null
                prefillHost = null
            },
            onDismiss = {
                showAddDialog = false
                editingServer = null
                prefillHost = null
            },
        )
    }
}
