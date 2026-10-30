package com.group80.studyhub

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private sealed interface UsersState {
    data object Loading : UsersState
    data class Error(val message: String) : UsersState
    data class Loaded(val users: List<UserSummary>) : UsersState
}

@Composable
fun AdminWelcomeScreen(
    user: LoggedInUser,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<UsersState>(UsersState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        state = UsersState.Loading
        UserDirectory.fetchAll()
            .onSuccess { state = UsersState.Loaded(it) }
            .onFailure { state = UsersState.Error(it.message ?: "Could not load users") }
    }

    AdminWelcomeContent(
        user = user,
        state = state,
        onRefresh = { reloadKey++ },
        onSignOut = onSignOut,
        modifier = modifier
    )
}

@Composable
private fun AdminWelcomeContent(
    user: LoggedInUser,
    state: UsersState,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Welcome ${user.firstName}! You are logged in as \"${displayRole(user.role)}\".",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val title = if (state is UsersState.Loaded) "All users (${state.users.size})" else "All users"
            Text(title, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        HorizontalDivider()

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                is UsersState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is UsersState.Error -> Text(
                    text = "Could not load users: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    textAlign = TextAlign.Center
                )
                is UsersState.Loaded ->
                    if (state.users.isEmpty()) {
                        Text("No users yet.", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.users, key = { it.uid }) { u ->
                                UserRow(user = u, onChanged = onRefresh)
                            }
                        }
                    }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out")
        }
    }
}

@Composable
private fun UserRow(user: UserSummary, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column {
        ListItem(
            headlineContent = { Text("Name: " + user.fullName) },
            supportingContent = {
                Text(
                    buildString {
                        append("Role: ").append(displayRole(user.role))
                        if (user.email.isNotBlank()) append("\n").append("Email: ").append(user.email)
                        append("\n").append("Status: ").append(if (user.disabled) "Disabled" else "Active")
                    }
                )
            },
            // != so admin cant delete itself, therefore the restruction form pd1 remains as there can only be 1 admin
            trailingContent = {
                if (user.role != "ADMIN") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            enabled = !busy,
                            modifier = Modifier.width(110.dp),
                            onClick = {
                                busy = true
                                error = null
                                scope.launch {
                                    UserAdminActions.setDisabled(user.uid, !user.disabled) // same call from method from bottom

                                        .onSuccess { busy = false; onChanged() }
                                        .onFailure { busy = false; error = it.message ?: "Some error has occured with disable/enable" }
                                }
                            }
                        ) { Text(if (user.disabled) "Enable" else "Disable") }

                        Button(
                            enabled = !busy,
                            modifier = Modifier.width(110.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            onClick = {
                                busy = true
                                error = null
                                scope.launch {
                                    UserAdminActions.delete(user.uid) //calls themethod from the bottom
                                        .onSuccess { busy = false; onChanged() }
                                        .onFailure { busy = false; error = it.message ?: "Some error has occured with deletion" }
                                }
                            }
                        ) { Text("Delete") }
                    }
                }
            }
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp, 4.dp))
        }
        HorizontalDivider()
    }
}

class AccountBlockedException(message: String) : Exception(message)

private object UserAdminActions {
    private val users = FirebaseDatabase.getInstance().reference.child("users")

    suspend fun setDisabled(uid: String, disabled: Boolean): Result<Unit> = try {
        users.child(uid).child("disabled").setValue(disabled).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun delete(uid: String): Result<Unit> = try {
        users.child(uid).removeValue().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}