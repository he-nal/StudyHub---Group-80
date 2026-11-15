package com.group80.studyhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.group80.studyhub.ui.theme.StudyHubTheme
import kotlinx.coroutines.launch

class CreateCourseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val courseId = intent.getStringExtra(EXTRA_COURSE_ID)
        setStudyHubContent { modifier ->
            CreateCourseScreen(
                courseId = courseId,
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
fun CreateCourseScreen(
    courseId: String?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editing = courseId != null
    var categories by remember { mutableStateOf<List<Category>?>(null) }
    var initial by remember { mutableStateOf<Course?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        categories = null
        initial = null
        loadError = null
        val categoriesResult = CategoryRepository.fetchAll()
        val courseResult = if (courseId != null) CourseRepository.get(courseId) else null
        val failure = categoriesResult.exceptionOrNull() ?: courseResult?.exceptionOrNull()
        if (failure != null) {
            loadError = failure.message ?: "Could not load"
        } else {
            categories = categoriesResult.getOrThrow()
            initial = courseResult?.getOrNull()
        }
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
            if (editing) "Edit course" else "Add course",
            style = MaterialTheme.typography.headlineMedium
        )

        val loadedCategories = categories
        when {
            loadError != null -> {
                Text("Could not load: $loadError", color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { reloadKey++ }) { Text("Retry") }
            }
            loadedCategories == null -> CircularProgressIndicator()
            loadedCategories.isEmpty() -> Text(
                "No categories exist yet. Create one with \"Manage categories\" first.",
                color = MaterialTheme.colorScheme.error
            )
            else -> CourseForm(
                courseId = courseId,
                initial = initial,
                categories = loadedCategories,
                onDone = onDone
            )
        }
    }
}

@Composable
private fun CourseForm(
    courseId: String?,
    initial: Course?,
    categories: List<Category>,
    onDone: () -> Unit
) {
    var code by rememberSaveable { mutableStateOf(initial?.code ?: "") }
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var description by rememberSaveable { mutableStateOf(initial?.description ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(initial?.categoryId) }
    var term by rememberSaveable { mutableStateOf(initial?.term) }
    var location by rememberSaveable { mutableStateOf(initial?.location ?: "") }
    var errors by remember { mutableStateOf(CourseFieldErrors()) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val termOptions = remember { (CourseValidator.termOptions() + listOfNotNull(initial?.term)).distinct() }
    val selectedCategory = categories.firstOrNull { it.id == categoryId }
    val editing = courseId != null

    fun submit() {
        serverError = null
        errors = CourseValidator.validate(code, title, selectedCategory != null, term, location, description)
        val category = selectedCategory
        val chosenTerm = term
        if (errors.hasErrors || category == null || chosenTerm == null) return

        saving = true
        scope.launch {
            val result = if (courseId == null) {
                CourseRepository.add(code, title, category, chosenTerm, location, description)
            } else {
                CourseRepository.update(courseId, code, title, category, chosenTerm, location, description)
            }
            result
                .onSuccess {
                    saving = false
                    onDone()
                }
                .onFailure {
                    saving = false
                    if (it is DuplicateCourseException) {
                        errors = CourseFieldErrors(code = it.message)
                    } else {
                        serverError = it.message ?: "Could not save course"
                    }
                }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it
                errors = errors.copy(code = null)
            },
            label = { Text("Course code") },
            singleLine = true,
            isError = errors.code != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it
                errors = errors.copy(title = null)
            },
            label = { Text("Course title") },
            singleLine = true,
            isError = errors.title != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = description,
            onValueChange = {
                description = it
                errors = errors.copy(description = null)
            },
            label = { Text("Description (optional)") },
            minLines = 2,
            maxLines = 4,
            isError = errors.description != null,

            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        DropdownField(
            label = "category",
            selected = selectedCategory?.name,
            options = categories.map { it.name },
            error = errors.category,
            onSelected = { index ->
                categoryId = categories[index].id
                errors = errors.copy(category = null)
            }
        )
        OutlinedTextField(
            value = term ?: "",
            onValueChange = {
                term = it
                errors = errors.copy(term = null)
            },
            label = { Text("Term") },
            singleLine = true,
            isError = errors.term != null,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = location,
            onValueChange = {
                location = it
                errors = errors.copy(location = null)
            },
            label = { Text("Location (optional)") },
            singleLine = true,
            isError = errors.location != null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
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
                    editing -> "Save"
                    else -> "Add course"
                }
            )
        }
    }
}

const val EXTRA_COURSE_ID = "com.group80.studyhub.COURSE_ID"

fun ComponentActivity.setStudyHubContent(content: @Composable (Modifier) -> Unit) {
    enableEdgeToEdge()
    setContent {
        StudyHubTheme {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                content(Modifier.padding(innerPadding))
            }
        }
    }
}
@Composable
fun DropdownField(
    label: String,
    selected: String?,
    options: List<String>,
    error: String?,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected ?: "Select $label")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(index)
                        }
                    )
                }
            }
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
}