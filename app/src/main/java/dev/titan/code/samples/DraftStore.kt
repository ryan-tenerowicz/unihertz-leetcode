package dev.titan.code.samples

import java.io.File

/**
 * M1's minimal draft store (PLAN §12): one file per problem in app storage, written atomically,
 * plus one backup file per problem that `sym+N` overwrites on each reset. The cursor position
 * is kept in a small side file. M2 migrates these into Room.
 */
class DraftStore(private val dir: File) {
    data class Draft(val text: String, val line: Int, val column: Int)

    init { dir.mkdirs() }

    fun load(slug: String): Draft? {
        val f = file(slug, "py")
        if (!f.exists()) return null
        val pos = file(slug, "pos").takeIf { it.exists() }?.readText()?.trim()?.split(":")
        return Draft(
            f.readText(),
            pos?.getOrNull(0)?.toIntOrNull() ?: 0,
            pos?.getOrNull(1)?.toIntOrNull() ?: 0,
        )
    }

    fun save(slug: String, draft: Draft) {
        write(file(slug, "py"), draft.text)
        write(file(slug, "pos"), "${draft.line}:${draft.column}")
    }

    /** Keeps the current draft before a reset; overwritten on every reset (PLAN §5.4 `sym+N`). */
    fun backup(slug: String, text: String) = write(file(slug, "backup.py"), text)

    fun loadBackup(slug: String): String? = file(slug, "backup.py").takeIf { it.exists() }?.readText()

    private fun file(slug: String, ext: String) = File(dir, "$slug.$ext")

    private fun write(target: File, text: String) {
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }
}
