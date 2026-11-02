package com.dyx.novelreader.ui.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dyx.novelreader.data.model.DiscoveredService
import com.dyx.novelreader.data.model.RemoteProtocol
import com.dyx.novelreader.data.model.RemoteServer
import com.dyx.novelreader.ui.common.IconTile
import com.dyx.novelreader.ui.common.SectionHeader
import com.dyx.novelreader.ui.common.TagPill
import com.dyx.novelreader.ui.common.headerBrush

/** 服务器列表：连接 SMB / WebDAV 书库 */
@Composable
fun ServerListScreen(
    servers: List<RemoteServer>,
    discovered: List<DiscoveredService>,
    discovering: Boolean,
    onOpenServer: (RemoteServer) -> Unit,
    onAddServer: () -> Unit,
    onEditServer: (RemoteServer) -> Unit,
    onDeleteServer: (RemoteServer) -> Unit,
    onToggleScan: () -> Unit,
    onPickDiscovered: (DiscoveredService) -> Unit,
    onBackToShelf: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<RemoteServer?>(null) }
    var actionTarget by remember { mutableStateOf<RemoteServer?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddServer,
                icon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                text = { Text("添加服务器") },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
        ) {
            Box(Modifier.fillMaxWidth().background(headerBrush()).statusBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 2.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackToShelf) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回书架")
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            "书库服务器",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "SMB · WebDAV · 局域网直连",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (servers.isNotEmpty()) {
                    item {
                        SectionHeader(title = "我的服务器", subtitle = "共 ${servers.size} 个")
                    }
                    items(servers, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            onClick = { onOpenServer(server) },
                            onLongClick = { actionTarget = server },
                        )
                    }
                }

                item {
                    SectionHeader(
                        title = "局域网发现",
                        subtitle = if (discovering) "正在扫描同一网络内的 SMB / WebDAV 服务…"
                        else "自动搜索同一网络内的 SMB / WebDAV 服务",
                        trailing = {
                            Surface(
                                onClick = onToggleScan,
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(38.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Rounded.Radar,
                                        contentDescription = if (discovering) "停止扫描" else "开始扫描",
                                        modifier = Modifier.size(18.dp),
                                        tint = if (discovering) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                    )
                }

                if (discovered.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (discovering) "正在搜索…" else "点右上角雷达图标开始扫描，或直接手动添加服务器地址",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    items(discovered, key = { "${it.host}:${it.port}:${it.protocol}" }) { service ->
                        DiscoveredCard(service = service, onClick = { onPickDiscovered(service) })
                    }
                }
            }
        }
    }

    actionTarget?.let { server ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(server.name) },
            text = {
                Column {
                    Text(server.addressLabel, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${server.protocol.label} · ${if (server.anonymous) "匿名访问" else "账号 ${server.username}"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    actionTarget = null
                    onEditServer(server)
                }) { Text("编辑") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        actionTarget = null
                        deleteTarget = server
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { actionTarget = null }) { Text("关闭") }
                }
            },
        )
    }

    deleteTarget?.let { server ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除服务器？") },
            text = { Text("只会移除「${server.name}」的连接配置，书架里的书和阅读进度都会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteServer(server)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ServerCard(
    server: RemoteServer,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isSamba = server.protocol == RemoteProtocol.SMB
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = if (isSamba) Icons.Rounded.Dns else Icons.Rounded.Cloud,
                container = if (isSamba) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.tertiaryContainer,
                tint = if (isSamba) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onTertiaryContainer,
                size = 44.dp,
                iconSize = 22.dp,
                shape = RoundedCornerShape(14.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    server.addressLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagPill(
                        text = server.protocol.label,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    if (server.secure) {
                        TagPill(
                            text = "HTTPS",
                            icon = Icons.Rounded.Lock,
                            container = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    TagPill(
                        text = if (server.anonymous) "匿名访问" else server.username.ifBlank { "有账号" },
                        icon = Icons.Rounded.Person,
                        container = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun DiscoveredCard(service: DiscoveredService, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(
                icon = Icons.Rounded.Radar,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                size = 38.dp,
                iconSize = 19.dp,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(service.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                Text(
                    "${service.host}:${service.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TagPill(
                text = service.protocol.label,
                container = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
