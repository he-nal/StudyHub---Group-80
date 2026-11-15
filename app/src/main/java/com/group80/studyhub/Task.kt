package com.group80.studyhub

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.cancellation.CancellationException

/** Status stored in the database as [key]; [label] is what the student sees. */
enum class TaskStatus(val key: String, val label: String) {
    TO_DO("TO_DO", "To do"),
    IN_PROGRESS("IN_PROGRESS", "In progress"),
    COMPLETED("COMPLETED", "Completed");

    companion object {
        fun fromKey(key: String?): TaskStatus = entries.firstOrNull { it.key == key } ?: TO_DO
    }
}

/**
 * A task belongs to one student and one course.
 * [dueDate] is stored as "yyyy-MM-dd" so it sorts correctly and has no time zone problems.
 */
data class Task(
    val id: String,
    val courseId: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val status: TaskStatus,
    val createdAt: Long
)

data class TaskFieldErrors(
    val title: String? = null,
    val description: String? = null,
    val dueDate: String? = null,
    val status: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(title, description, dueDate, status).any { it != null }
}

/** Helpers for the "yyyy-MM-dd" due dates. The Material date picker works in UTC, so we do too. */
object TaskDates {
    private const val PATTERN = "yyyy-MM-dd"

    private fun utcFormat() = SimpleDateFormat(PATTERN, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }

    /** Millis returned by the date picker (UTC midnight) -> "yyyy-MM-dd". */
    fun fromPickerMillis(millis: Long): String = utcFormat().format(Date(millis))

    /** "yyyy-MM-dd" -> millis the date picker understands, or null if it is not a real date. */
    fun toPickerMillis(date: String): Long? = try {
        utcFormat().parse(date)?.time
    } catch (e: ParseException) {
        null
    }

    fun isValid(date: String): Boolean = Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(date) && toPickerMillis(date) != null

    /** Today's date in the phone's own time zone. */
    fun today(): String = SimpleDateFormat(PATTERN, Locale.US).format(Date())

    /** Friendly text such as "Nov 20, 2026". Falls back to the raw text if it can't be parsed. */
    fun display(date: String): String {
        val millis = toPickerMillis(date) ?: return date
        return DateFormat.getDateInstance(DateFormat.MEDIUM).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
    }
}

object TaskValidator {
    const val TITLE_MIN = 2
    const val TITLE_MAX = 100
    const val DESCRIPTION_MAX = 500

    private val SPACES = Regex("\\s+")

    fun normalizeTitle(title: String): String = title.trim().replace(SPACES, " ")

    /**
     * @param originalDueDate when editing, the date the task already has. Keeping an old date that
     *        is now in the past is allowed; choosing a new past date is not.
     */
    fun validate(
        title: String,
        description: String,
        dueDate: String?,
        status: TaskStatus?,
        originalDueDate: String? = null
    ): TaskFieldErrors {
        val cleanTitle = normalizeTitle(title)
        val cleanDescription = description.trim()
        return TaskFieldErrors(
            title = when {
                cleanTitle.isEmpty() -> "Task title is required"
                cleanTitle.length < TITLE_MIN -> "Title must be at least $TITLE_MIN characters"
                cleanTitle.length > TITLE_MAX -> "Title must be $TITLE_MAX characters or fewer"
                else -> null
            },
            description = if (cleanDescription.length > DESCRIPTION_MAX) {
                "Description must be $DESCRIPTION_MAX characters or fewer"
            } else null,
            dueDate = when {
                dueDate.isNullOrBlank() -> "Choose a due date"
                !TaskDates.isValid(dueDate) -> "Enter a valid due date"
                dueDate < TaskDates.today() && dueDate != originalDueDate ->
                    "Due date can't be in the past"
                else -> null
            },
            status = if (status == null) "Choose a status" else null
        )
    }
}

/**
 * Tasks live under userTasks/{uid}/{courseId}/{taskId}, so every task is owned by exactly one
 * student and courses shared between students never share tasks.
 */
object TaskRepository {
    private val root = FirebaseDatabase.getInstance().reference
    private val userTasks = root.child("userTasks")

    private fun currentUid(): String =
        FirebaseAuth.getInstance().currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("You are not logged in")

    private fun courseTasks(courseId: String) = userTasks.child(currentUid()).child(courseId)

    private fun DataSnapshot.toTask(courseId: String) = Task(
        id = key.orEmpty(),
        courseId = courseId,
        title = child("title").getValue(String::class.java).orEmpty(),
        description = child("description").getValue(String::class.java).orEmpty(),
        dueDate = child("dueDate").getValue(String::class.java).orEmpty(),
        status = TaskStatus.fromKey(child("status").getValue(String::class.java)),
        createdAt = child("createdAt").getValue(Long::class.java) ?: 0L
    )

    suspend fun fetchForCourse(courseId: String): Result<List<Task>> = try {
        require(courseId.isNotBlank()) { "Missing course id" }
        val snapshot = courseTasks(courseId).get().await()
        Result.success(
            snapshot.children
                .map { it.toTask(courseId) }
                .sortedWith(compareBy<Task>({ it.dueDate }, { it.createdAt }))
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun get(courseId: String, taskId: String): Result<Task> = try {
        require(courseId.isNotBlank()) { "Missing course id" }
        require(taskId.isNotBlank()) { "Missing task id" }
        val snap = courseTasks(courseId).child(taskId).get().await()
        if (!snap.exists()) throw IllegalStateException("This task no longer exists")
        Result.success(snap.toTask(courseId))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun add(
        courseId: String,
        title: String,
        description: String,
        dueDate: String,
        status: TaskStatus
    ): Result<Task> = try {
        require(courseId.isNotBlank()) { "Missing course id" }
        val cleanTitle = TaskValidator.normalizeTitle(title)
        val cleanDescription = description.trim()

        val ref = courseTasks(courseId).push()
        val now = System.currentTimeMillis()
        ref.setValue(
            mapOf(
                "title" to cleanTitle,
                "description" to cleanDescription,
                "dueDate" to dueDate,
                "status" to status.key,
                "createdAt" to now
            )
        ).await()
        Result.success(
            Task(ref.key.orEmpty(), courseId, cleanTitle, cleanDescription, dueDate, status, now)
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Changes the editable information of an existing task. createdAt is left untouched. */
    suspend fun update(
        courseId: String,
        taskId: String,
        title: String,
        description: String,
        dueDate: String,
        status: TaskStatus
    ): Result<Task> = try {
        require(courseId.isNotBlank()) { "Missing course id" }
        require(taskId.isNotBlank()) { "Missing task id" }
        val cleanTitle = TaskValidator.normalizeTitle(title)
        val cleanDescription = description.trim()

        val current = get(courseId, taskId).getOrThrow() // fails if the task was deleted meanwhile
        courseTasks(courseId).child(taskId).updateChildren(
            mapOf(
                "title" to cleanTitle,
                "description" to cleanDescription,
                "dueDate" to dueDate,
                "status" to status.key
            )
        ).await()
        Result.success(
            Task(taskId, courseId, cleanTitle, cleanDescription, dueDate, status, current.createdAt)
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
