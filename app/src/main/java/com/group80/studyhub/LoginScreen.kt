package com.group80.studyhub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.group80.studyhub.ui.theme.StudyHubTheme
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (LoggedInUser) -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // What the user has typed so far. Changing these redraws the screen.
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    // Error messages to show. null means "no error".
    var usernameError by rememberSaveable { mutableStateOf<String?>(null) }
    var passwordError by rememberSaveable { mutableStateOf<String?>(null) }
    var loginError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun attemptLogin() {
        usernameError = if (username.isBlank()) "Email is required" else null
        passwordError = if (password.isBlank()) "Password is required" else null
        loginError = null


        // Stop here if either field is empty; the messages above will show.
        if (usernameError != null || passwordError != null) return
        scope.launch {
            AuthRepository.login(username.trim(), password).onSuccess { onLoginSuccess(it) }.onFailure { loginError = "Invalid username or password" }
        }

    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("StudyHub", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                usernameError = null  // hide the error once they start typing
            },
            label = { Text("Email") },
            isError = usernameError != null,
            supportingText = { usernameError?.let { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                passwordError = null
            },
            label = { Text("Password") },
            isError = passwordError != null,
            supportingText = { passwordError?.let { Text(it) } },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        // Shown only when both fields were filled in but didn't match an account.
        loginError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { attemptLogin() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Log in")
        }

        TextButton(onClick = onSignUpClick) {
            Text("Don't have an account? Sign up")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    StudyHubTheme {
        LoginScreen(onLoginSuccess = {}, onSignUpClick = {})
    }
}
