package com.group80.studyhub

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**Src: // https://www.youtube.com/watch?v=SwyjhKCz6Xk
 * Used this video for the suspend coroutine + scope
 */

data class LoggedInUser(
    val firstName: String,
    val role: String
)

object AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    suspend fun login(email: String, password: String): Result<LoggedInUser> = try {
        val uid = auth.signInWithEmailAndPassword(email, password).await().user!!.uid
        Result.success(loadProfile(uid))
    } catch (e: Exception) {
        Result.failure(e)
    }
    //

 //getting the profile by the user id
    private suspend fun loadProfile(uid: String): LoggedInUser {
        val database = db.child("users").child(uid).get().await()
        return LoggedInUser(
            firstName = database.child("firstName").getValue(String::class.java) ?: "",
            role = database.child("role").getValue(String::class.java) ?: "STUDENT"
        )
    }
    suspend fun register(
        first: String,
        last: String,
        email: String,
        password: String,
        asLeader: Boolean
    ): Result<Unit> = try {
        val cleanEmail = email.trim().lowercase()
        val uid = auth.createUserWithEmailAndPassword(cleanEmail, password).await().user!!.uid
        db.child("users").child(uid).setValue(
            mapOf(
                "firstName" to first.trim(),
                "lastName" to last.trim(),
                "email" to cleanEmail,
                "role" to if (asLeader) "STUDY_GROUP_LEADER" else "STUDENT"
            )
        ).await()
        auth.signOut() // they log in normally afterwards
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }



    fun signOut() = auth.signOut()
}