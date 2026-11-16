package com.group80.studyhub

import com.google.firebase.auth.FirebaseAuth

/**
 * Our rule for a course's tasks: deleting the course deletes every member's tasks for it too.
 * Used by both delete buttons (student's "My courses" and the admin's course list) so the rule is the same.
 * Order matters:
 *  1. read who is in the course first, because CourseRepository.delete() also removes that list;
 *  2. delete the course, so if that fails nobody's tasks are lost;
 *  3. delete the tasks of every member (plus the signed-in user, in case they weren't listed).
 */
suspend fun deleteCourseAndTasks(courseId: String): Result<Unit> {
    val memberIds = CourseRepository.fetchMemberIds(courseId).getOrElse { return Result.failure(it) }
    val myUid = FirebaseAuth.getInstance().currentUser?.uid
    CourseRepository.delete(courseId).getOrElse { return Result.failure(it) }
    return TaskRepository.deleteAllForCourse(courseId, memberIds + listOfNotNull(myUid))
        .recoverCatching { throw IllegalStateException("Course deleted, but its tasks could not be removed") }
}
