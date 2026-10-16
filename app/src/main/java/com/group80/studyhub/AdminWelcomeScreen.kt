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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group80.studyhub.ui.theme.StudyHubTheme

private sealed interface UsersState {
    data object Loading : UsersState
    data class Error(val message: String) : UsersState
    data class Loaded(val users: List<UserSummary>) : UsersState
}

/**
 * Welcome screen for the ADMIN role (Optional - Admin User Management).
 * Same welcome message as WelcomeScreen, plus the list of every user created so far.
 */
@Composable
fun AdminWelcomeScreen(
    user: LoggedInUser,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<UsersState>(UsersState.Loading) }
    var reloadKey by remember { mutableIntStateOf(0) }

    // Loads when the screen opens and again every time Refresh bumps reloadKey.
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
                                Column {
                                    ListItem(
                                        headlineContent = { Text("Name: " + u.fullName) },
                                        supportingContent = {
                                            Text(
                                                buildString {
                                                    append("Role: ").append(displayRole(u.role))
                                                    if (u.email.isNotBlank()) append("\n").append("Email: ").append(u.email)
                                                }
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
        Button(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out")
        }
    }
}
