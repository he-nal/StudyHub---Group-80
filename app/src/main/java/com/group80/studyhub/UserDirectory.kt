package com.group80.studyhub

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

/** One row in the admin's "all users" list. */
data class UserSummary(
    val uid: String,
    val firstName: String,
    val email: String,
    val role: String
) {
    val fullName: String
        get() = listOf(firstName).filter { it.isNotBlank() }.joinToString(" ")
            .ifBlank { "(no name)" }
}

/**
 * TEMPORARY: returns sample data so the admin screen can be seen and tested.
 * Jubair: replace the body of fetchAll() with the real database read.
 *
 * Update 2026-10-16
 * It is important to note that firstName is the only thing on Database.
 * To add a Last Name, we can change previous listOf and add to AuthRepo accordingly.
 */
object UserDirectory {
    //private val
    private val database = FirebaseDatabase.getInstance().reference
    suspend fun fetchAll(): Result<List<UserSummary>> {
        try{
        val total = database.child("users").get().await()
        val diffUsers = mutableListOf<UserSummary>()
        for(child in total.children) {
            val summary = UserSummary(
                uid = child.key ?: "",
                firstName = child.child("firstName").getValue(String::class.java) ?: "",
                email = child.child("email").getValue(String::class.java) ?: "",
                role = child.child("role").getValue(String::class.java) ?: "STUDENT"
            )
            diffUsers.add(summary)
        }
        return Result.success(diffUsers)
        }
        catch (e: Exception) {
            return Result.failure(e)
        }
    }

}