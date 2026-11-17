package com.itzfallenme.katmusic.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.itzfallenme.katmusic.desktop.DesktopSettingsStore
import kotlinx.coroutines.launch

@Composable
fun DesktopLibrarySettingsSection(
    store: DesktopSettingsStore,
    onChooseFolder: suspend () -> Unit,
    onRefresh: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    val folders = remember(refreshKey) { store.libraryFolders() }

    Text(
        "Library folders",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        "Music is indexed in place. Removing a folder only removes it from KatMusic; files are never deleted.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
    if (folders.isEmpty()) {
        Text(
            "No folders added.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    folders.forEach { folder ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                folder,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    store.removeLibraryFolder(folder)
                    refreshKey++
                    scope.launch { onRefresh() }
                },
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove folder $folder")
            }
        }
    }
    OutlinedButton(
        onClick = {
            scope.launch {
                onChooseFolder()
                refreshKey++
            }
        },
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
        Text("  Add music folder")
    }
}
