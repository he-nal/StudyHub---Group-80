package com.group80.studyhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun RegisterScreen(
    service: RegistrationService,
    onRegistered: () -> Unit,
    modifier: Modifier = Modifier
) {
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var asLeader by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(FieldErrors()) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Create account", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = first, onValueChange = { first = it },
            label = { Text("First name") }, singleLine = true,
            isError = errors.first != null,
            supportingText = { errors.first?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = last, onValueChange = { last = it },
            label = { Text("Last name") }, singleLine = true,
            isError = errors.last != null,
            supportingText = { errors.last?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true,
            isError = errors.email != null,
            supportingText = { errors.email?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Password") }, singleLine = true,
            isError = errors.password != null,
            supportingText = { errors.password?.let { Text(it) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirm, onValueChange = { confirm = it },
            label = { Text("Confirm password") }, singleLine = true,
            isError = errors.confirm != null,
            supportingText = { errors.confirm?.let { Text(it) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = !asLeader, onClick = { asLeader = false })
            Text("Student")
            Spacer(Modifier.width(16.dp))
            RadioButton(selected = asLeader, onClick = { asLeader = true })
            Text("Study Group Leader")
        }

        serverError?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                serverError = null
                errors = AccountValidator.validate(first, last, email, password, confirm)
                if (!errors.hasErrors) {
                    loading = true
                    service.register(first, last, email, password, confirm, asLeader) { result ->
                        loading = false
                        result
                            .onSuccess { onRegistered() }
                            .onFailure { serverError = it.message }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Creating..." else "Create account")
        }
    }
}