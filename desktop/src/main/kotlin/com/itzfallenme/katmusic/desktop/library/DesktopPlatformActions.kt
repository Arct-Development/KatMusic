package com.itzfallenme.katmusic.desktop.library

import com.itzfallenme.katmusic.desktop.DesktopSettingsStore
import com.itzfallenme.katmusic.shared.library.PlatformActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop-only platform actions. Picking a music folder from disk is real
 * here; Android reports no such capability, so the shared UI hides the action
 * rather than showing a control that cannot work.
 */
class DesktopPlatformActions(
    private val store: DesktopSettingsStore,
    private val library: DesktopMediaLibrary,
) : PlatformActions {

    override val canChooseFolder: Boolean = true

    override suspend fun chooseLibraryFolder(): String? = withContext(Dispatchers.Main) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Choose a music folder"
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isMultiSelectionEnabled = false
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
        val path = chooser.selectedFile?.absolutePath ?: return@withContext null
        store.addLibraryFolder(path)
        library.refresh()
        path
    }

    /** Chooses an external `.m3u`/`.m3u8` file for the shared Playlists screen. */
    suspend fun choosePlaylistFile(): File? = withContext(Dispatchers.Main) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Import M3U playlist"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = false
            fileFilter = FileNameExtensionFilter("M3U playlists", "m3u", "m3u8")
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
        chooser.selectedFile
    }
}
