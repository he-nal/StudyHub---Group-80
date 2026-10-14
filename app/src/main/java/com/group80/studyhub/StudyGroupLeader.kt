package com.group80.studyhub

class StudyGroupLeader(
    id: String,
    firstName: String,
    lastName: String,
    email: String,
    password: String
) : Student(id, firstName, lastName, email, password) {

    override val role: String = "STUDY_GROUP_LEADER"
    val ledGroupIds: MutableList<String> = mutableListOf()
}