package com.group80.studyhub

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

fun displayRole(role: String): String = when (role) {
    "ADMIN" -> "Administrator"
    "STUDENT" -> "Student"
    "STUDY_GROUP_LEADER" -> "Study Group Leader"
    else -> role
}

private sealed interface MyCoursesState {
    data object Loading : MyCoursesState
    data class Error(val message: String) : MyCoursesState
    data class Loaded(val courses: List<Course>) : MyCoursesState
}

@Composable
fun WelcomeScreen(
    user: LoggedInUser,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<MyCoursesState>(MyCoursesState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Course?>(null) } // course waiting for "Delete?" confirmation
    var deleting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    // Opens CreateCourseActivity in edit mode. It finishes with RESULT_OK after saving,
    // so reload the list then to show the new values.
    val editCourseLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) reloadKey++
    }

    LaunchedEffect(reloadKey) {
        state = MyCoursesState.Loading
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            state = MyCoursesState.Error("Not logged in")
            return@LaunchedEffect
        }
        CourseRepository.fetchForUser(uid)
            .onSuccess { state = MyCoursesState.Loaded(it) }
            .onFailure { state = MyCoursesState.Error(it.message ?: "Could not load courses") }
    }

    // Rule for a course's tasks: deleting the course deletes all of them too, and the dialog says so.
    pendingDelete?.let { course ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text("Delete course?") },
            text = {
                Text(
                    "\"${course.code} – ${course.title}\" and all of its tasks will be " +
                        "permanently deleted. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        actionError = null
                        scope.launch {
                            deleteCourseAndTasks(course.id)
                                .onSuccess { reloadKey++ }
                                .onFailure {
                                    actionError = it.message ?: "Could not delete course"
                                    reloadKey++ // the course may be gone even if its tasks weren't removed
                                }
                            deleting = false
                            pendingDelete = null
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Welcome ${user.firstName}! You are logged in as \"${displayRole(user.role)}\".",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title = when (state) {
                is MyCoursesState.Loaded -> "My courses (${(state as MyCoursesState.Loaded).courses.size})"
                else -> "My courses"
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { reloadKey++ }) { Text("Refresh") }
        }
        HorizontalDivider()

        actionError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                is MyCoursesState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                is MyCoursesState.Error -> {
                    Text(
                        text = s.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
                is MyCoursesState.Loaded -> {
                    if (s.courses.isEmpty()) {
                        Text(
                            "No courses assigned yet.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(s.courses, key = { it.id }) { course ->
                                ListItem(
                                    headlineContent = { Text(course.code) },
                                    supportingContent = {
                                        Text("${course.title}\n${course.term}")
                                    },
                                    trailingContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    // passing the course id is what puts the form in edit mode
                                                    editCourseLauncher.launch(
                                                        Intent(context, CreateCourseActivity::class.java)
                                                            .putExtra(EXTRA_COURSE_ID, course.id)
                                                    )
                                                },
                                                modifier = Modifier.width(100.dp)
                                            ) { Text("Edit") }
                                            OutlinedButton(
                                                onClick = { pendingDelete = course },
                                                modifier = Modifier.width(100.dp)
                                            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        context.startActivity(
                                            Intent(context, CourseDetailActivity::class.java)
                                                .putExtra(EXTRA_COURSE_ID, course.id)
                                        )
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign out")
        }
    }
}