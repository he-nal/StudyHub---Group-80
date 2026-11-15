package com.group80.studyhub

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.launch

private sealed interface CoursesState {
    data object Loading : CoursesState
    data class Error(val message: String) : CoursesState
    data class Loaded(val courses: List<Course>) : CoursesState
}

@Composable
fun CourseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CoursesState>(CoursesState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<Course?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        state = CoursesState.Loading
        CourseRepository.fetchAll()
            .onSuccess { state = CoursesState.Loaded(it) }
            .onFailure { state = CoursesState.Error(it.message ?: "Could not load courses") }
    }

    pendingDelete?.let { course ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text("Delete course?") },
            text = { Text("\"${course.code} – ${course.title}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        actionError = null
                        scope.launch {
                            CourseRepository.delete(course.id)
                                .onSuccess {
                                    deleting = false
                                    pendingDelete = null
                                    reloadKey++
                                }
                                .onFailure {
                                    deleting = false
                                    pendingDelete = null
                                    actionError = it.message ?: "Could not delete course"
                                }
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Courses", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title = if (state is CoursesState.Loaded)
                "All courses (${(state as CoursesState.Loaded).courses.size})"
            else "All courses"
            Text(title, style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { reloadKey++ }) { Text("Refresh") }
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(context, CreateCourseActivity::class.java)
                        )
                    },
                    enabled = state is CoursesState.Loaded
                ) {
                    Text("Add course")
                }
            }
        }
        HorizontalDivider()

        actionError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                is CoursesState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                is CoursesState.Error ->
                    Text(
                        "Could not load courses: ${s.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                is CoursesState.Loaded ->
                    if (s.courses.isEmpty()) {
                        Text(
                            "No courses yet. Tap \"Add course\".",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(s.courses, key = { it.id }) { course ->
                                ListItem(
                                    headlineContent = { Text(course.code) },
                                    supportingContent = {
                                        Text("${course.title}\n${course.term} · ${course.categoryName}")
                                    },
                                    trailingContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            OutlinedButton(
                                                onClick = {
                                                    // Edit
                                                    context.startActivity(
                                                        Intent(context, CreateCourseActivity::class.java)
                                                            .putExtra(EXTRA_COURSE_ID, course.id)
                                                    )
                                                },
                                                modifier = Modifier.width(100.dp)
                                            ) { Text("Edit") }
                                            OutlinedButton(
                                                onClick = {
                                                    context.startActivity(
                                                        Intent(context, AssignCourseActivity::class.java)
                                                            .putExtra(EXTRA_COURSE_ID, course.id)
                                                    )},
                                                modifier = Modifier.width(100.dp)
                                            ){Text("assign")}

                                            OutlinedButton(
                                                onClick = { pendingDelete = course },
                                                modifier = Modifier.width(100.dp)
                                            ) {
                                                Text("Delete", color = MaterialTheme.colorScheme.error)
                                            }
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