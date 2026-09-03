package id.orbitcontrol.ui

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native Windows save/open operations replacing Android's share sheet. */
internal fun saveCopyWithDialog(source: File, title: String): Result<File> = runCatching {
    val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
        file = source.name
        isVisible = true
    }
    val filename = dialog.file ?: return Result.failure(IllegalStateException("Penyimpanan dibatalkan."))
    val target = File(dialog.directory ?: return Result.failure(IllegalStateException("Folder tujuan tidak tersedia.")), filename)
    source.copyTo(target, overwrite = true)
    target
}

internal fun openFileLocation(file: File): Result<Unit> = runCatching {
    val desktop = Desktop.getDesktop()
    if (desktop.isSupported(Desktop.Action.OPEN)) desktop.open(file.parentFile)
    else error("Windows tidak menyediakan pembuka folder untuk aplikasi ini.")
}
