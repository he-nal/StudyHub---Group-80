package com.group80.studyhub

// Only what the welcome screen needs after a successful login.
data class LoggedInUser(
    val firstName: String,
    val role: String
)

// TEMPORARY: stands in for the database login until it exists.
object FakeLogin {

    // Set by MainActivity so registered accounts can log in.
    var registeredLookup: ((String, String) -> LoggedInUser?)? = null

    fun login(username: String, password: String): LoggedInUser? {
        return when {
            username == "admin" && password == "XPI76SZUqyCjVxgnUjm0" ->
                LoggedInUser("admin", "ADMIN")
            username == "student1" && password == "pass123" ->
                LoggedInUser("Sam", "STUDENT")
            username == "leader1" && password == "pass123" ->
                LoggedInUser("Lee", "STUDY_GROUP_LEADER")
            else -> registeredLookup?.invoke(username, password)
        }
    }
}