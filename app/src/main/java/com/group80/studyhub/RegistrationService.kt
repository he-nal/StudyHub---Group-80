package com.group80.studyhub

import java.util.UUID

// TEMPORARY: accounts are kept in this list until the database is connected.
// Teammate: replace the duplicate-email check and users.add(...) with the real database calls.
class RegistrationService {

    private val users = mutableListOf<User>()

    fun register(
        first: String, last: String, email: String,
        password: String, confirm: String,
        asLeader: Boolean,
        onResult: (Result<User>) -> Unit
    ) {
        val f = first.trim()
        val l = last.trim()
        val e = email.trim().lowercase()

        if (AccountValidator.validate(f, l, e, password, confirm).hasErrors) {
            onResult(Result.failure(IllegalArgumentException("Please fix the highlighted fields")))
            return
        }
        if (users.any { it.email == e }) {
            onResult(Result.failure(IllegalArgumentException("Email already registered")))
            return
        }

        val id = UUID.randomUUID().toString()
        val user: User =
            if (asLeader) StudyGroupLeader(id, f, l, e, password)
            else Student(id, f, l, e, password)

        users.add(user)
        onResult(Result.success(user))
    }
}