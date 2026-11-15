package com.itzfallenme.katmusic.shared.library

import com.itzfallenme.katmusic.shared.models.Track
import kotlinx.coroutines.flow.StateFlow

/** Progress/result of a library scan, for the shared UI to render. */
data class ScanState(
    val isScanning: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

/**
 * The device's playable items. Android implements this over MediaStore and
 * the app's existing download database; desktop implements it over the local
 * folders the user picked.
 */
interface MediaLibrary {
    val tracks: StateFlow<List<Track>>
    val scan: StateFlow<ScanState>
    suspend fun refresh()
}

/**
 * Platform actions the shared UI can offer but only some platforms have.
 * Desktop can pick a folder from disk; Android has no user-picked music
 * folder, so it reports [canChooseFolder] = false and the UI hides the action
 * instead of showing a control that cannot work.
 */
interface PlatformActions {
    val canChooseFolder: Boolean
    suspend fun chooseLibraryFolder(): String?
}
