package com.group80.studyhub

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group80.studyhub.ui.theme.StudyHubTheme

class TaskListActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val courseId = intent.getStringExtra(EXTRA_COURSE_ID).orEmpty()
        setStudyHubContent { modifier ->
            TaskListScreen(courseId = courseId, onBack = { finish() }, modifier = modifier)
        }
    }
}

private sealed interface TaskListState {
    data object Loading : TaskListState
    data class Error(val message: String) : TaskListState
    data class Loaded(val course: Course, val tasks: List<Task>) : TaskListState
}

@Composable
fun TaskListScreen(
    courseId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<TaskListState>(TaskListState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }

    // The create/edit screen finishes with RESULT_OK when it saved something, so reload the list.
    val formLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) reloadKey++
    }

    LaunchedEffect(reloadKey, courseId) {
        state = TaskListState.Loading
        val courseResult = CourseRepository.get(courseId)
        val tasksResult = TaskRepository.fetchForCourse(courseId)
        val failure = courseResult.exceptionOrNull() ?: tasksResult.exceptionOrNull()
        state = if (failure != null) {
            TaskListState.Error(failure.message ?: "Could not load tasks")
        } else {
            TaskListState.Loaded(courseResult.getOrThrow(), tasksResult.getOrThrow())
        }
    }

    TaskListContent(
        state = state,
        onBack = onBack,
        onRefresh = { reloadKey++ },
        onAdd = {
            formLauncher.launch(
                Intent(context, TaskFormActivity::class.java)
                    .putExtra(EXTRA_COURSE_ID, courseId)
            )
        },
        onEdit = { task ->
            formLauncher.launch(
                Intent(context, TaskFormActivity::class.java)
                    .putExtra(EXTRA_COURSE_ID, courseId)
                    .putExtra(EXTRA_TASK_ID, task.id)
            )
        },
        modifier = modifier
    )
}

@Composable
private fun TaskListContent(
    state: TaskListState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Tasks", style = MaterialTheme.typography.headlineSmall)
        if (state is TaskListState.Loaded) {
            Text(
                "${state.course.code} – ${state.course.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title = if (state is TaskListState.Loaded) "My tasks (${state.tasks.size})" else "My tasks"
            Text(title, style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRefresh) { Text("Refresh") }
                Button(onClick = onAdd, enabled = state is TaskListState.Loaded) {
                    Text("Add task")
                }
            }
        }
        HorizontalDivider()

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is TaskListState.Loading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is TaskListState.Error ->
                    Text(
                        "Could not load tasks: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        textAlign = TextAlign.Center
                    )

                is TaskListState.Loaded ->
                    if (state.tasks.isEmpty()) {
                        Text(
                            "No tasks for this course yet.\nTap \"Add task\" to create your first one.",
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.tasks, key = { it.id }) { task ->
                                ListItem(
                                    headlineContent = { Text(task.title) },
                                    supportingContent = {
                                        Column {
                                            Text(
                                                "Due ${TaskDates.display(task.dueDate)} · ${task.status.label}"
                                            )
                                            if (task.description.isNotBlank()) {
                                                Text(
                                                    task.description,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    },
                                    trailingContent = {
                                        OutlinedButton(onClick = { onEdit(task) }) { Text("Edit") }
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

@Preview(showBackground = true)
@Composable
private fun TaskListPreview() {
    StudyHubTheme {
        TaskListContent(
            state = TaskListState.Loaded(
                Course(
                    "1", "SEG 2105", "Intro to Software Engineering", "c1", "Computer Science",
                    "Fall 2026", "", 1_700_000_000_000L
                ),
                listOf(
                    Task(
                        "t1", "1", "Deliverable 3", "Task list, create and edit task",
                        "2026-11-15", TaskStatus.IN_PROGRESS, 1_700_000_000_000L
                    )
                )
            ),
            onBack = {}, onRefresh = {}, onAdd = {}, onEdit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TaskListEmptyPreview() {
    StudyHubTheme {
        TaskListContent(
            state = TaskListState.Loaded(
                Course("1", "SEG 2105", "Intro to Software Engineering", "c1", "CS", "Fall 2026", "", 0L),
                emptyList()
            ),
            onBack = {}, onRefresh = {}, onAdd = {}, onEdit = {}
        )
    }
}
