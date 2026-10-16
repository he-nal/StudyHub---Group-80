package com.group80.studyhub

import java.util.UUID

// TEMPORARY: single shared in-memory store until the database is connected.
class RegistrationService {

    private val users = mutableListOf<User>()

    @Synchronized
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
        if (users.any { it.email.equals(e, ignoreCase = true) }) {
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

    @Synchronized
    fun findByCredentials(email: String, password: String): User? {
        val e = email.trim().lowercase()
        return users.firstOrNull { it.email.equals(e, ignoreCase = true) && it.password == password }
    }
    @Synchronized
    fun allUsers(): List<User> = users.toList()
}
