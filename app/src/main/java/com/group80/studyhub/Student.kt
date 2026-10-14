package com.group80.studyhub

open class Student(
    id: String,
    firstName: String,
    lastName: String,
    email: String,
    password: String
) : User(id, firstName, lastName, email, password) {

    override val role: String = "STUDENT"
    val courseIds: MutableList<String> = mutableListOf()
}