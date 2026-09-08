package uz.komil.mediapro.storage

import android.content.Context
import uz.komil.mediapro.R

/**
 * Operation folders under Downloads/MVD/, named in the user's chosen UI
 * language (spec section 7). The translated folder labels are real string
 * resources, so a user switching language starts saving under the new name;
 * already-saved files keep their original path in the history record.
 */
enum class MediaFolder(@androidx.annotation.StringRes val labelRes: Int) {
    DOWNLOADS(R.string.folder_downloads),     // plain link downloads
    TRIMMED(R.string.folder_trimmed),         // trimmed video/audio
    EDITED(R.string.folder_edited),           // intervals removed
    MIXED(R.string.folder_mixed),             // audio replaced / mixed
    AUDIO(R.string.folder_audio),             // extracted / converted audio
    RECORDINGS(R.string.folder_recordings)    // live stream recordings
}

object MediaFolders {
    const val ROOT = "MVD"

    /** e.g. "MVD/Yuklamalar" — first path segment stays under Download root. */
    fun relativePath(ctx: Context, folder: MediaFolder): String =
        "$ROOT/${ctx.getString(folder.labelRes)}"

    fun displayName(ctx: Context, folder: MediaFolder): String = ctx.getString(folder.labelRes)
}
