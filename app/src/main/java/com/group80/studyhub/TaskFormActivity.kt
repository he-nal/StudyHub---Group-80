package com.group80.studyhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

const val EXTRA_TASK_ID = "com.group80.studyhub.TASK_ID"

/** One screen for both "Add task" (no task id) and "Edit task" (task id given). */
class TaskFormActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val courseId = intent.getStringExtra(EXTRA_COURSE_ID).orEmpty()
        val taskId = intent.getStringExtra(EXTRA_TASK_ID)
        setStudyHubContent { modifier ->
            TaskFormScreen(
                courseId = courseId,
                taskId = taskId,
                onBack = { finish() },
                onDone = {
                    setResult(RESULT_OK)
                    finish()
                },
                modifier = modifier
            )
        }
    }
}

@Composable
fun TaskFormScreen(
    courseId: String,
    taskId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editing = taskId != null
    var initial by remember { mutableStateOf<Task?>(null) }
    var loaded by remember { mutableStateOf(!editing) } // adding needs nothing loaded
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        if (taskId == null) return@LaunchedEffect
        loaded = false
        loadError = null
        TaskRepository.get(courseId, taskId)
            .onSuccess {
                initial = it
                loaded = true
            }
            .onFailure { loadError = it.message ?: "Could not load task" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(
            if (editing) "Edit task" else "Add task",
            style = MaterialTheme.typography.headlineMedium
        )

        when {
            loadError != null -> {
                Text("Could not load: $loadError", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { reloadKey++ }) { Text("Retry") }
            }
            !loaded -> CircularProgressIndicator()
            else -> TaskForm(
                courseId = courseId,
                taskId = taskId,
                initial = initial,
                onDone = onDone
            )
        }
    }
}

@Composable
private fun TaskForm(
    courseId: String,
    taskId: String?,
    initial: Task?,
    onDone: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var dueDate by rememberSaveable { mutableStateOf(initial?.dueDate) }
    // saved as the status key so it survives rotation
    var statusKey by rememberSaveable { mutableStateOf((initial?.status ?: TaskStatus.TO_DO).key) }
    var errors by remember { mutableStateOf(TaskFieldErrors()) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val editing = taskId != null
    val status = TaskStatus.fromKey(statusKey)

    fun submit() {
        serverError = null
        errors = TaskValidator.validate(
            title = title,
            description = description,
            dueDate = dueDate,
            status = status,
            originalDueDate = initial?.dueDate
        )
        val chosenDate = dueDate
        if (errors.hasErrors || chosenDate == null) return

        saving = true
        scope.launch {
            val result = if (taskId == null) {
                TaskRepository.add(courseId, title, description, chosenDate, status)
            } else {
                TaskRepository.update(courseId, taskId, title, description, chosenDate, status)
            }
            result
                .onSuccess {
                    saving = false
                    onDone()
                }
                .onFailure {
                    saving = false
                    serverError = it.message ?: "Could not save task"
                }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                errors = errors.copy(title = null)
            },
            label = { Text("Title") },
            singleLine = true,
            isError = errors.title != null,
            supportingText = { errors.title?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
                errors = errors.copy(description = null)
            },
            label = { Text("Description (optional)") },
            minLines = 3,
            maxLines = 6,
            isError = errors.description != null,
            supportingText = { errors.description?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        DueDateField(
            value = dueDate,
            error = errors.dueDate,
            onSelected = {
                dueDate = it
                errors = errors.copy(dueDate = null)
            }
        )
        DropdownField(
            label = "status",
            selected = status.label,
            options = TaskStatus.entries.map { it.label },
            error = errors.status,
            onSelected = { index ->
                statusKey = TaskStatus.entries[index].key
                errors = errors.copy(status = null)
            }
        )

        serverError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { submit() },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    saving && editing -> "Saving..."
                    saving -> "Adding..."
                    editing -> "Save changes"
                    else -> "Add task"
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDateField(
    value: String?,
    error: String?,
    onSelected: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Column {
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text(value?.let { TaskDates.display(it) } ?: "Select due date")
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.let { TaskDates.toPickerMillis(it) }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onSelected(TaskDates.fromPickerMillis(it)) }
                        showPicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
