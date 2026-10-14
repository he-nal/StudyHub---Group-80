package com.group80.studyhub

abstract class User(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
    var isEnabled: Boolean = true
) {
    abstract val role: String
}