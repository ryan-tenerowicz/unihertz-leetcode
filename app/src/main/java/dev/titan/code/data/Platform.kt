package dev.titan.code.data

/**
 * The three problem sites (PLAN §6.1, rev 13). Every problem has one string key used everywhere
 * (drafts, test cases, the active problem): LeetCode keys are the bare slug, as before, so saved
 * data carries over; the others are prefixed (`nc:two-integer-sum`, `cf:4A`). The store's
 * `provider` column holds [id].
 *
 * The editor is the same on all three. What differs is listed here, so the UI hides what a
 * platform can't do.
 */
enum class Platform(
    val id: String,
    val prefix: String,
    val title: String,
    /** Run the tests on the platform's judge (`sym+E`). */
    val remoteRun: Boolean,
    /** Comments and community solutions; NeetCode has its own solution article instead. */
    val comments: Boolean,
    /** How the code is called: a `Solution` method (LeetCode, NeetCode) or a program reading stdin. */
    val stdin: Boolean,
) {
    LEETCODE("leetcode", "", "LeetCode", remoteRun = true, comments = true, stdin = false),
    NEETCODE("neetcode", "nc:", "NeetCode", remoteRun = true, comments = false, stdin = false),
    CODEFORCES("codeforces", "cf:", "Codeforces", remoteRun = false, comments = false, stdin = true);

    fun key(siteId: String) = prefix + siteId

    /** The site's own id for a key: `nc:two-integer-sum` → `two-integer-sum`. */
    fun siteId(key: String) = key.removePrefix(prefix)

    companion object {
        fun of(key: String): Platform = when {
            key.startsWith(NEETCODE.prefix) -> NEETCODE
            key.startsWith(CODEFORCES.prefix) -> CODEFORCES
            else -> LEETCODE
        }

        fun byId(id: String?): Platform = entries.firstOrNull { it.id == id } ?: LEETCODE
    }
}
