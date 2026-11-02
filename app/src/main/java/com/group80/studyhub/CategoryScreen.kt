package com.group80.studyhub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group80.studyhub.ui.theme.StudyHubTheme
import kotlinx.coroutines.launch

private sealed interface CategoriesState {
    data object Loading : CategoriesState
    data class Error(val message: String) : CategoriesState
    data class Loaded(val categories: List<Category>) : CategoriesState
}


 //Admin screen for the course-category catalogue: list, add, edit and delete.
 //Reached from the "Manage categories" button on AdminWelcomeScreen.

@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CategoriesState>(CategoriesState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<Category?>(null) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        state = CategoriesState.Loading
        CategoryRepository.fetchAll()
            .onSuccess { state = CategoriesState.Loaded(it) }
            .onFailure { state = CategoriesState.Error(it.message ?: "Could not load categories") }
    }

    val existingNames = (state as? CategoriesState.Loaded)?.categories?.map { it.name }.orEmpty()

    if (showAdd) {
        CategoryFormDialog(
            title = "Add category",
            confirmText = "Add",
            savingText = "Adding...",
            initialName = "",
            initialDescription = "",
            existingNames = existingNames,
            save = { name, description -> CategoryRepository.add(name, description) },
            onDismiss = { showAdd = false },
            onSaved = {
                showAdd = false
                actionError = null
                reloadKey++
            }
        )
    }

    pendingEdit?.let { category ->
        CategoryFormDialog(
            title = "Edit category",
            confirmText = "Save",
            savingText = "Saving...",
            initialName = category.name,
            initialDescription = category.description,
            //leave out this category's own name, so keeping it isn't flagged as a duplicate.
            existingNames = (state as? CategoriesState.Loaded)?.categories
                ?.filter { it.id != category.id }?.map { it.name }.orEmpty(),
            save = { name, description -> CategoryRepository.update(category.id, name, description) },
            onDismiss = { pendingEdit = null },
            onSaved = {
                pendingEdit = null
                actionError = null
                reloadKey++
            }
        )
    }

    pendingDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text("Delete category?") },
            text = { Text("\"${category.name}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        actionError = null
                        scope.launch {
                            CategoryRepository.delete(category.id)
                                .onSuccess {
                                    deleting = false
                                    pendingDelete = null
                                    reloadKey++
                                }
                                .onFailure {
                                    deleting = false
                                    pendingDelete = null
                                    actionError = it.message ?: "Could not delete category"
                                }
                        }
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }

    CategoryScreenContent(
        state = state,
        actionError = actionError,
        onBack = onBack,
        onRefresh = { reloadKey++ },
        onAddClick = { showAdd = true },
        onEditClick = { pendingEdit = it },
        onDeleteClick = { pendingDelete = it },
        modifier = modifier
    )
}

@Composable
private fun CategoryScreenContent(
    state: CategoriesState,
    actionError: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (Category) -> Unit,
    onDeleteClick: (Category) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Course categories", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title =
                if (state is CategoriesState.Loaded) "All categories (${state.categories.size})" else "All categories"
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onRefresh) { Text("Refresh") }
                // Only once the list is loaded, so the duplicate-name check has the existing names.
                Button(onClick = onAddClick, enabled = state is CategoriesState.Loaded) {
                    Text("Add category")
                }
            }
        }
        HorizontalDivider()

        actionError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is CategoriesState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is CategoriesState.Error -> Text(
                    text = "Could not load categories: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    textAlign = TextAlign.Center
                )
                is CategoriesState.Loaded ->
                    if (state.categories.isEmpty()) {
                        Text("No categories yet. Tap \"Add category\".", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.categories, key = { it.id }) { category ->
                                CategoryRow(
                                    category = category,
                                    onEdit = { onEditClick(category) },
                                    onDelete = { onDeleteClick(category) }
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, onEdit: () -> Unit, onDelete: () -> Unit) {
    Column {
        ListItem(
            headlineContent = { Text("Name: " + category.name) },
            supportingContent = { Text("Description: " + category.description) },
            trailingContent = {
                //stacked like the Disable/Delete buttons on AdminWelcomeScreen, so they fit on narrow phones.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.width(100.dp)) {
                        Text("Edit")
                    }
                    OutlinedButton(onClick = onDelete, modifier = Modifier.width(100.dp)) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        )
        HorizontalDivider()
    }
}

//Shared form for "Add category" and "Edit category": same fields, same validation.
//save is the database call to make (add or update); it only runs once the fields are valid.
@Composable
private fun CategoryFormDialog(
    title: String,
    confirmText: String,
    savingText: String,
    initialName: String,
    initialDescription: String,
    existingNames: List<String>,
    save: suspend (name: String, description: String) -> Result<Category>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var errors by remember { mutableStateOf(CategoryFieldErrors()) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        serverError = null
        errors = CategoryValidator.validate(name, description, existingNames)
        if (errors.hasErrors) return

        saving = true
        scope.launch {
            save(name, description)
                .onSuccess {
                    saving = false
                    onSaved()
                }
                .onFailure {
                    saving = false
                    if (it is DuplicateCategoryException) {
                        errors = CategoryFieldErrors(name = it.message)
                    } else {
                        serverError = it.message ?: "Could not save category"
                    }
                }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errors = errors.copy(name = null)
                    },
                    label = { Text("Category name") },
                    singleLine = true,
                    isError = errors.name != null,
                    supportingText = { Text(errors.name ?: "${name.length}/${CategoryValidator.NAME_MAX}") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        errors = errors.copy(description = null)
                    },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 4,
                    isError = errors.description != null,
                    supportingText = {
                        Text(errors.description ?: "${description.length}/${CategoryValidator.DESCRIPTION_MAX}")
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                serverError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(enabled = !saving, onClick = { submit() }) {
                Text(if (saving) savingText else confirmText)
            }
        },
        dismissButton = {
            TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun CategoryScreenPreview() {
    StudyHubTheme {
        CategoryScreenContent(
            state = CategoriesState.Loaded(
                listOf(
                    Category("1", "Computer Science", "Programming, algorithms and software engineering"),
                    Category("2", "Mathematics", "Calculus, algebra and statistics")
                )
            ),
            actionError = null,
            onBack = {},
            onRefresh = {},
            onAddClick = {},
            onEditClick = {},
            onDeleteClick = {}
        )
    }
}
