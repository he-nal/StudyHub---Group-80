package com.group80.studyhub

import android.content.Intent
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