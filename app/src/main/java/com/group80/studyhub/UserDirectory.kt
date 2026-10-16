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
 * TEMPORARY: reads accounts from the in-memory RegistrationService.
 * Replace the body of fetchAll() with the real database read later.
 */
object UserDirectory {
    suspend fun fetchAll(): Result<List<UserSummary>> {
        delay(300)
        return Result.success(
            registrationService.allUsers().map { u ->
                UserSummary(
                    uid = u.id,
                    firstName = u.firstName,
                    lastName = u.lastName,
                    email = u.email,
                    role = if (u is StudyGroupLeader) "STUDY_GROUP_LEADER" else "STUDENT"
                )
            }
        )
    }
}