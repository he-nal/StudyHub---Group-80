package com.group80.studyhub

// Only what the welcome screen needs after a successful login.
data class LoggedInUser(
    val firstName: String,
    val role: String
)

// TEMPORARY: stands in for Person 1's database login until it exists.
// When it does, replace the body of login() with the real database call.
// The screens only depend on this function, so they won't need to change.
object FakeLogin {

    fun login(username: String, password: String): LoggedInUser? {
        return when {
            username == "admin" && password == "XPI76SZUqyCjVxgnUjm0" ->
                LoggedInUser("admin", "ADMIN")
            username == "student1" && password == "pass123" ->
                LoggedInUser("Sam", "STUDENT")
            username == "leader1" && password == "pass123" ->
                LoggedInUser("Lee", "STUDY_GROUP_LEADER")
            else -> null
        }
    }
}
