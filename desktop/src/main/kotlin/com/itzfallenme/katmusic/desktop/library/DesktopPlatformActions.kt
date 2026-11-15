package com.itzfallenme.katmusic.desktop.library

import com.itzfallenme.katmusic.desktop.DesktopSettingsStore
import com.itzfallenme.katmusic.shared.library.PlatformActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser

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
}
