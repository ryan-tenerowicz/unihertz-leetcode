package dev.titan.code.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * The offline-first store (PLAN §8.1): every screen reads from here; providers only refresh it.
 * Rows are keyed by (provider, slug) so later platforms (PLAN §6.3) share the tables.
 * Lists (tags, hints) are stored as JSON text.
 */

@Entity(tableName = "problems", primaryKeys = ["provider", "slug"])
data class ProblemRow(
    val provider: String,
    val slug: String,
    val frontendId: Int,
    val title: String,
    val difficulty: String,
    val paidOnly: Boolean,
    val acRate: Double,
    /** Tag slugs, comma-separated. */
    val tags: String,
    val status: String?,
)

@Entity(tableName = "details", primaryKeys = ["provider", "slug"])
data class DetailRow(
    val provider: String,
    val slug: String,
    val questionId: String,
    val frontendId: Int,
    val title: String,
    val difficulty: String,
    val paidOnly: Boolean,
    val contentHtml: String?,
    val starter: String?,
    val exampleTestcases: String,
    val metaData: String,
    val hintsJson: String,
    val tags: String,
    val similarJson: String,
    /** When it was saved from the network; 0 for bundled samples (refreshed on the next online open). */
    val fetchedAt: Long,
)

/** Saved comments and solutions for one problem, as JSON (PLAN §8.2). */
@Entity(tableName = "discussions", primaryKeys = ["provider", "slug"])
data class DiscussionRow(
    val provider: String,
    val slug: String,
    val commentsJson: String,
    val solutionsJson: String,
    val fetchedAt: Long,
)

/** An opened solution post: body and the comment pages loaded so far (rev 10). */
@Entity(tableName = "posts", primaryKeys = ["provider", "topicId"])
data class PostRow(
    val provider: String,
    val topicId: Long,
    val slug: String,
    val body: String,
    val commentsJson: String,
    val totalComments: Int,
    val pagesLoaded: Int,
    val fetchedAt: Long,
)

/** A saved test input (PLAN §8.3); examples aren't stored, they come from the statement. */
@Entity(tableName = "test_cases", indices = [Index(value = ["provider", "slug", "input"], unique = true)])
data class TestCaseRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val slug: String,
    val input: String,
    val expected: String?,
    val source: String,
    val verified: Boolean,
    val createdAt: Long,
)

/** Submission history (PLAN §8.2), with the code that was submitted. */
@Entity(tableName = "submissions", indices = [Index("provider", "slug")])
data class SubmissionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val slug: String,
    val submissionId: String,
    val status: String,
    val runtime: String,
    val memory: String,
    val passed: String,
    val code: String,
    val at: Long,
    /** The practice timer's reading when this was submitted (rev 24); null if it wasn't running. */
    val elapsedMs: Long? = null,
)

/** The practice timer for one problem (rev 24): time spent, from the first edit, while the app was in front. */
@Entity(tableName = "timers", primaryKeys = ["provider", "slug"])
data class TimerRow(
    val provider: String,
    val slug: String,
    val elapsedMs: Long,
    /** When the first edit started it. */
    val startedAt: Long,
    /** When an accepted submission stopped it; null while it's running. */
    val doneAt: Long?,
)


/** An offline pack (PLAN §8.4): a named set of problems to keep saved. */
@Entity(tableName = "packs")
data class PackRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** "plan:<slug>" for a LeetCode study plan, "list" for a saved list view, "list:<id>" for a
     *  curated list, or "all:<platform>" for everything on a site (rev 25). */
    val source: String,
    val slugsJson: String,
    val createdAt: Long,
)

@Entity(tableName = "drafts", primaryKeys = ["provider", "slug"])
data class DraftRow(
    val provider: String,
    val slug: String,
    val text: String,
    val line: Int,
    val col: Int,
    val updatedAt: Long,
)

/** The short draft version history (PLAN §8.2). */
@Entity(tableName = "draft_versions", indices = [Index("provider", "slug")])
data class DraftVersionRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val provider: String,
    val slug: String,
    val text: String,
    val savedAt: Long,
    /** "reset", "opened", "periodic" or "migrated". */
    val reason: String,
)

@Entity(tableName = "meta")
data class MetaRow(@PrimaryKey val key: String, val value: String)

@Dao
interface StoreDao {
    @Query("SELECT * FROM problems WHERE provider = :provider ORDER BY frontendId")
    suspend fun problems(provider: String): List<ProblemRow>

    @Upsert suspend fun upsertProblems(rows: List<ProblemRow>)

    /** Problems that aren't Premium or Pro, for "Everything on …" (rev 25). */
    @Query("SELECT COUNT(*) FROM problems WHERE provider = :provider AND paidOnly = 0")
    suspend fun freeProblemCount(provider: String): Int

    @Query("SELECT * FROM details WHERE provider = :provider AND slug = :slug")
    suspend fun detail(provider: String, slug: String): DetailRow?

    @Upsert suspend fun upsertDetail(row: DetailRow)

    @Query("SELECT slug FROM details WHERE provider = :provider AND fetchedAt > 0")
    suspend fun savedSlugs(provider: String): List<String>

    @Query("SELECT COUNT(*) FROM details WHERE fetchedAt > 0")
    suspend fun savedDetailCount(): Int

    @Query("DELETE FROM details WHERE fetchedAt > 0")
    suspend fun clearDetails()

    @Query("SELECT * FROM discussions WHERE provider = :provider AND slug = :slug")
    suspend fun discussion(provider: String, slug: String): DiscussionRow?

    @Upsert suspend fun upsertDiscussion(row: DiscussionRow)

    @Query("SELECT COUNT(*) FROM discussions")
    suspend fun discussionCount(): Int

    @Query("DELETE FROM discussions")
    suspend fun clearDiscussions()

    @Query("SELECT * FROM posts WHERE provider = :provider AND topicId = :topicId")
    suspend fun post(provider: String, topicId: Long): PostRow?

    @Upsert suspend fun upsertPost(row: PostRow)

    @Query("DELETE FROM posts")
    suspend fun clearPosts()

    @Query("SELECT * FROM test_cases WHERE provider = :provider AND slug = :slug ORDER BY createdAt")
    suspend fun testCases(provider: String, slug: String): List<TestCaseRow>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTestCase(row: TestCaseRow): Long

    @Query("UPDATE test_cases SET expected = :expected, verified = 1 WHERE provider = :provider AND slug = :slug AND input = :input")
    suspend fun verifyTestCase(provider: String, slug: String, input: String, expected: String)

    @Query("DELETE FROM test_cases WHERE id = :id")
    suspend fun deleteTestCase(id: Long)

    /** Your own expected output on a case the judge hasn't verified (rev 24). */
    @Query("UPDATE test_cases SET expected = :expected WHERE provider = :provider AND slug = :slug AND input = :input AND verified = 0")
    suspend fun setUnverifiedExpected(provider: String, slug: String, input: String, expected: String?)

    @Insert suspend fun insertSubmission(row: SubmissionRow)

    @Query("SELECT * FROM submissions WHERE provider = :provider AND slug = :slug ORDER BY at DESC LIMIT 20")
    suspend fun submissions(provider: String, slug: String): List<SubmissionRow>

    @Query("UPDATE problems SET status = :status WHERE provider = :provider AND slug = :slug")
    suspend fun setStatus(provider: String, slug: String, status: String)

    @Query("SELECT * FROM packs ORDER BY createdAt")
    suspend fun packs(): List<PackRow>

    @Query("SELECT * FROM packs WHERE id = :id")
    suspend fun pack(id: Long): PackRow?

    @Insert suspend fun insertPack(row: PackRow): Long

    @Query("UPDATE packs SET slugsJson = :slugsJson WHERE id = :id")
    suspend fun setPackSlugs(id: Long, slugsJson: String)

    @Query("DELETE FROM packs WHERE id = :id")
    suspend fun deletePack(id: Long)

    @Query("SELECT slug FROM discussions WHERE provider = :provider")
    suspend fun discussionSlugs(provider: String): List<String>

    @Query("DELETE FROM details WHERE provider = :provider AND fetchedAt > 0 AND slug IN (:slugs)")
    suspend fun deleteDetails(provider: String, slugs: List<String>)

    @Query("DELETE FROM discussions WHERE provider = :provider AND slug IN (:slugs)")
    suspend fun deleteDiscussions(provider: String, slugs: List<String>)

    @Query("SELECT * FROM drafts WHERE provider = :provider AND slug = :slug")
    fun draft(provider: String, slug: String): DraftRow?

    @Query("SELECT slug FROM drafts WHERE provider = :provider")
    suspend fun draftSlugs(provider: String): List<String>

    @Upsert fun upsertDraft(row: DraftRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertVersion(row: DraftVersionRow)

    @Query("SELECT * FROM draft_versions WHERE provider = :provider AND slug = :slug ORDER BY savedAt DESC")
    fun versions(provider: String, slug: String): List<DraftVersionRow>

    @Query(
        """DELETE FROM draft_versions WHERE provider = :provider AND slug = :slug AND id NOT IN
           (SELECT id FROM draft_versions WHERE provider = :provider AND slug = :slug ORDER BY savedAt DESC LIMIT :keep)"""
    )
    fun trimVersions(provider: String, slug: String, keep: Int)

    @Transaction
    fun addVersion(row: DraftVersionRow, keep: Int) {
        insertVersion(row)
        trimVersions(row.provider, row.slug, keep)
    }

    @Query("SELECT * FROM timers WHERE provider = :provider AND slug = :slug")
    suspend fun timer(provider: String, slug: String): TimerRow?

    @Upsert suspend fun putTimer(row: TimerRow)

    @Query("DELETE FROM timers WHERE provider = :provider AND slug = :slug")
    suspend fun deleteTimer(provider: String, slug: String)

    @Query("SELECT value FROM meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Upsert suspend fun putMeta(row: MetaRow)
}

@Database(
    entities = [ProblemRow::class, DetailRow::class, DiscussionRow::class, PostRow::class, DraftRow::class, DraftVersionRow::class, MetaRow::class,
        TestCaseRow::class, SubmissionRow::class, PackRow::class, TimerRow::class],
    version = 5,
    // Each version only adds (v2 posts, v3 test cases and submissions, v4 packs, v5 timers and
    // the submission's timer reading); drafts are kept.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
)
abstract class StoreDb : RoomDatabase() {
    abstract fun dao(): StoreDao

    companion object {
        @Volatile private var instance: StoreDb? = null

        /** One instance per process: the activity and the background workers share it. */
        fun open(context: Context): StoreDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, StoreDb::class.java, "titan-code.db")
                .build().also { instance = it }
        }
    }
}
