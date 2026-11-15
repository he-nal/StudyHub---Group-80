package com.group80.studyhub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group80.studyhub.ui.theme.StudyHubTheme
import java.text.DateFormat
import java.util.Date

class CourseDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val courseId = intent.getStringExtra(EXTRA_COURSE_ID).orEmpty()
        setStudyHubContent { modifier ->
            CourseDetailScreen(courseId = courseId, onBack = { finish() }, modifier = modifier)
        }
    }
}

private sealed interface CourseDetailState {
    data object Loading : CourseDetailState
    data class Error(val message: String) : CourseDetailState
    data class Loaded(val course: Course) : CourseDetailState
}

@Composable
fun CourseDetailScreen(
    courseId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<CourseDetailState>(CourseDetailState.Loading) }

    LaunchedEffect(courseId) {
        state = CourseDetailState.Loading
        CourseRepository.get(courseId)
            .onSuccess { state = CourseDetailState.Loaded(it) }
            .onFailure { state = CourseDetailState.Error(it.message ?: "Could not load course") }
    }

    CourseDetailContent(
        state = state,
        onBack = onBack,
        onOpenTasks = {
            context.startActivity(
                Intent(context, TaskListActivity::class.java)
                    .putExtra(EXTRA_COURSE_ID, courseId)
            )
        },
        modifier = modifier
    )
}

@Composable
private fun CourseDetailContent(
    state: CourseDetailState,
    onBack: () -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is CourseDetailState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is CourseDetailState.Error -> Text(
                    text = "Could not load course: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    textAlign = TextAlign.Center
                )
                is CourseDetailState.Loaded -> CourseDetails(state.course, onOpenTasks)
            }
        }
    }
}

@Composable
private fun CourseDetails(course: Course, onOpenTasks: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(course.code, style = MaterialTheme.typography.headlineMedium)
        Text(course.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Button(onClick = onOpenTasks, modifier = Modifier.fillMaxWidth()) { Text("View tasks") }
        HorizontalDivider()

        DetailRow("Course code", course.code)
        DetailRow("Title", course.title)
        // description and location are optional, so say so instead of showing an empty line
        DetailRow("Description", course.description.ifBlank { "No description" })
        DetailRow("Category", course.categoryName.ifBlank { "Not specified" })
        DetailRow("Term", course.term)
        DetailRow("Location", course.location.ifBlank { "Not specified" })
        if (course.createdAt > 0L) {
            DetailRow("Added on", DateFormat.getDateInstance().format(Date(course.createdAt)))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview(showBackground = true)
@Composable
private fun CourseDetailPreview() {
    StudyHubTheme {
        CourseDetailContent(
            state = CourseDetailState.Loaded(
                Course(
                    "1", "SEG 2105", "Intro to Software Engineering", "c1", "Computer Science",
                    "Fall 2026", "", 1_700_000_000_000L,
                    description = "Requirements, design and testing of software systems."
                )
            ),
            onBack = {},
            onOpenTasks = {}
        )
    }
}