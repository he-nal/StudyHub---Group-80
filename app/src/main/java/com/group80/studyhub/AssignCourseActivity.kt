package com.group80.studyhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class AssignCourseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val courseId = intent.getStringExtra(EXTRA_COURSE_ID).orEmpty()
        setStudyHubContent { modifier ->
            AssignCourseScreen(
                courseId = courseId,
                onBack = { finish() },
                modifier = modifier
            )
        }
    }
}

private sealed interface AssignState {
    data object Loading : AssignState
    data class Error(val message: String) : AssignState
    data class Loaded(
        val course: Course,
        val users: List<UserSummary>,
        val memberIds: Set<String>
    ) : AssignState
}

@Composable
fun AssignCourseScreen(
    courseId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<AssignState>(AssignState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var busyUid by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(reloadKey) {
        state = AssignState.Loading
        val courseResult = CourseRepository.get(courseId)
        val usersResult = UserDirectory.fetchAll()
        val membersResult = CourseRepository.fetchMemberIds(courseId)

        val failure = courseResult.exceptionOrNull()
            ?: usersResult.exceptionOrNull()
            ?: membersResult.exceptionOrNull()

        if (failure != null) {
            state = AssignState.Error(failure.message ?: "Could not load")
        } else {
            state = AssignState.Loaded(
                course = courseResult.getOrThrow(),
                users = usersResult.getOrThrow().filter { it.role != "ADMIN" },
                memberIds = membersResult.getOrThrow()
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }

        when (val s = state) {
            is AssignState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is AssignState.Error -> {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center),
                    textAlign = TextAlign.Center
                )
            }
            is AssignState.Loaded -> {
                Text("Assign students", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${s.course.code} – ${s.course.title}",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(s.course.term, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()

                if (s.users.isEmpty()) {
                    Text(
                        "No students or leaders yet.",
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .wrapContentSize(Alignment.Center)
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(s.users, key = { it.uid }) { user ->
                            val isAssigned = user.uid in s.memberIds
                            ListItem(
                                headlineContent = { Text(user.fullName) },
                                supportingContent = {
                                    Text(
                                        buildString {
                                            append(displayRole(user.role))
                                            if (user.email.isNotBlank()) {
                                                append(" · ")
                                                append(user.email)
                                            }
                                            append("\n")
                                            append(if (isAssigned) "Assigned" else "Not assigned")
                                        }
                                    )
                                },
                                trailingContent = {
                                    OutlinedButton(
                                        enabled = busyUid == null,
                                        onClick = {
                                            busyUid = user.uid
                                            scope.launch {
                                                val result = if (isAssigned) {
                                                    CourseRepository.unassign(courseId, user.uid)
                                                } else {
                                                    CourseRepository.assign(courseId, user.uid)
                                                }
                                                busyUid = null
                                                if (result.isSuccess) reloadKey++
                                            }
                                        },
                                        modifier = Modifier.width(110.dp)
                                    ) {
                                        Text(if (isAssigned) "Unassign" else "Assign")
                                    }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}