package com.group80.studyhub

import kotlinx.coroutines.delay

/** One row in the admin's "all users" list. */
data class UserSummary(
    val uid: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val role: String
) {
    val fullName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { "(no name)" }
}

/**
 * TEMPORARY: returns sample data so the admin screen can be seen and tested.
 * Jubair: replace the body of fetchAll() with the real database read.
 */
object UserDirectory {
    suspend fun fetchAll(): Result<List<UserSummary>> {
        delay(300)
        return Result.success(
            listOf(
                UserSummary("1", "Sam", "Student", "sam@example.com", "STUDENT"),
                UserSummary("2", "Lee", "Leader", "lee@example.com", "STUDY_GROUP_LEADER"),
                UserSummary("3", "Ava", "Student", "ava@example.com", "STUDENT")
            )
        )
    }
}