package com.group80.studyhub

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import kotlin.coroutines.cancellation.CancellationException


data class Course(
    val id: String,
    val code: String,
    val title: String,
    val categoryId: String,
    val categoryName: String,
    val term: String,
    val location: String,
    val createdAt: Long,
    val description: String = ""
)

class DuplicateCourseException : Exception("This course code already exists in that term")


data class CourseFieldErrors(
    val code: String? = null,
    val title: String? = null,
    val category: String? = null,
    val term: String? = null,
    val location: String? = null,
    val description: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(code, title, category, term, location, description).any { it != null }
}

object CourseValidator {
    const val TITLE_MIN = 2
    const val TITLE_MAX = 100
    const val LOCATION_MAX = 60
    const val DESCRIPTION_MAX = 300

    private val SPACES = Regex("\\s+")

    fun normalizeCode(code: String): String = code.trim().replace(Regex("\\s+"), " ").uppercase()

    fun normalizeTitle(title: String): String = title.trim().replace(SPACES, " ")

    fun sameCourse(codeA: String, termA: String, codeB: String, termB: String): Boolean =
        normalizeCode(codeA) == normalizeCode(codeB) && termA == termB

    fun termOptions(): List<String> {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        return (year - 1..year + 1).flatMap { y -> listOf("Winter $y") }
    }



    fun validate(
        code: String,
        title: String,
        hasCategory: Boolean,
        term: String?,
        location: String,
        description: String = ""
    ): CourseFieldErrors {
        val cleanCode = code.trim()
        val cleanTitle = normalizeTitle(title)
        val cleanLocation = location.trim()
        val cleanDescription = description.trim()
        return CourseFieldErrors(
            code = when {
                cleanCode.isEmpty() -> "Course code is required"
                else -> null
            },
            title = when {
                cleanTitle.isEmpty() -> "Course title is required"
                cleanTitle.length < TITLE_MIN -> "Title must be at least $TITLE_MIN characters"
                cleanTitle.length > TITLE_MAX -> "Title must be $TITLE_MAX characters or fewer"
                else -> null
            },
            category = if (!hasCategory) "Choose a category" else null,
            term = if (term.isNullOrBlank()) "Choose a term" else null,
            location = if (cleanLocation.length > LOCATION_MAX) {
                "Location must be $LOCATION_MAX characters or fewer"
            } else null,
            description = if (cleanDescription.length > DESCRIPTION_MAX) {
                "Description must be $DESCRIPTION_MAX characters or fewer"
            } else null
        )
    }
}

object CourseRepository {
    private val root = FirebaseDatabase.getInstance().reference
    private val courses = root.child("courses")
    private val userCourses = root.child("userCourses")
    private val courseMembers = root.child("courseMembers")

    private fun DataSnapshot.toCourse() = Course(
        id = key.orEmpty(),
        code = child("code").getValue(String::class.java).orEmpty(),
        title = child("title").getValue(String::class.java).orEmpty(),
        categoryId = child("categoryId").getValue(String::class.java).orEmpty(),
        categoryName = child("categoryName").getValue(String::class.java).orEmpty(),
        term = child("term").getValue(String::class.java).orEmpty(),
        location = child("location").getValue(String::class.java).orEmpty(),
        createdAt = child("createdAt").getValue(Long::class.java) ?: 0L,
        description = child("description").getValue(String::class.java).orEmpty()
    )


    suspend fun fetchAll(): Result<List<Course>> = try {
        val snapshot = courses.get().await()
        Result.success(snapshot.children.map { it.toCourse() }.sortedBy { it.code })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun fetchForUser(uid: String): Result<List<Course>> = try {
        require(uid.isNotBlank()) { "Missing user id" }
        val ids = userCourses.child(uid).get().await().children.mapNotNull { it.key }
        val list = ids.mapNotNull { id ->
            val snap = courses.child(id).get().await()
            if (snap.exists()) snap.toCourse() else null // skip ids of courses that no longer exist
        }.sortedBy { it.code }
        Result.success(list)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun get(courseId: String): Result<Course> = try {
        require(courseId.isNotBlank()) { "Missing course id" }
        val snap = courses.child(courseId).get().await()
        if (!snap.exists()) throw IllegalStateException("This course no longer exists")
        Result.success(snap.toCourse())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }


    suspend fun add(
        code: String,
        title: String,
        category: Category,
        term: String,
        location: String,
        description: String = ""
    ): Result<Course> = try {
        val cleanCode = CourseValidator.normalizeCode(code)
        val cleanTitle = CourseValidator.normalizeTitle(title)
        val cleanLocation = location.trim()
        val cleanDescription = description.trim()

        val existing = fetchAll().getOrThrow()
        if (existing.any { CourseValidator.sameCourse(it.code, it.term, cleanCode, term) }) {
            throw DuplicateCourseException()
        }

        val ref = courses.push()
        val now = System.currentTimeMillis()
        ref.setValue(
            mapOf(
                "code" to cleanCode,
                "title" to cleanTitle,
                "description" to cleanDescription,
                "categoryId" to category.id,
                "categoryName" to category.name,
                "term" to term,
                "location" to cleanLocation,
                "createdAt" to now
            )
        ).await()
        Result.success(
            Course(
                ref.key.orEmpty(), cleanCode, cleanTitle, category.id, category.name,
                term, cleanLocation, now, cleanDescription
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun update(
        id: String,
        code: String,
        title: String,
        category: Category,
        term: String,
        location: String,
        description: String = ""
    ): Result<Course> = try {
        require(id.isNotBlank()) { "Missing course id" }
        val cleanCode = CourseValidator.normalizeCode(code)
        val cleanTitle = CourseValidator.normalizeTitle(title)
        val cleanLocation = location.trim()
        val cleanDescription = description.trim()

        val existing = fetchAll().getOrThrow()
        val current = existing.firstOrNull { it.id == id }
            ?: throw IllegalStateException("This course no longer exists")
        // skip the course itself, so keeping the same code + term isn't a "duplicate".
        if (existing.any { it.id != id && CourseValidator.sameCourse(it.code, it.term, cleanCode, term) }) {
            throw DuplicateCourseException()
        }

        courses.child(id).updateChildren(
            mapOf(
                "code" to cleanCode,
                "title" to cleanTitle,
                "description" to cleanDescription,
                "categoryId" to category.id,
                "categoryName" to category.name,
                "term" to term,
                "location" to cleanLocation
            )
        ).await()
        Result.success(
            Course(
                id, cleanCode, cleanTitle, category.id, category.name,
                term, cleanLocation, current.createdAt, cleanDescription
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun delete(id: String): Result<Unit> = try {
        require(id.isNotBlank()) { "Missing course id" }
        val memberIds = fetchMemberIds(id).getOrThrow()
        val updates = mutableMapOf<String, Any?>(
            "courses/$id" to null,
            "courseMembers/$id" to null
        )
        memberIds.forEach { uid -> updates["userCourses/$uid/$id"] = null }
        root.updateChildren(updates).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }


    suspend fun fetchMemberIds(courseId: String): Result<Set<String>> = try {
        require(courseId.isNotBlank()) { "Missing course id" }
        val snap = courseMembers.child(courseId).get().await()
        Result.success(snap.children.mapNotNull { it.key }.toSet())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun assign(courseId: String, uid: String): Result<Unit> = try {
        require(courseId.isNotBlank() && uid.isNotBlank()) { "Missing id" }
        root.updateChildren(
            mapOf(
                "userCourses/$uid/$courseId" to true,
                "courseMembers/$courseId/$uid" to true
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun unassign(courseId: String, uid: String): Result<Unit> = try {
        require(courseId.isNotBlank() && uid.isNotBlank()) { "Missing id" }
        root.updateChildren(
            mapOf<String, Any?>(
                "userCourses/$uid/$courseId" to null,
                "courseMembers/$courseId/$uid" to null
            )
        ).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}