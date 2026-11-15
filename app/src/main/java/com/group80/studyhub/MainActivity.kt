package com.group80.studyhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.group80.studyhub.ui.theme.StudyHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyHubTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StudyHubApp(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}


private val LoggedInUserSaver = listSaver<LoggedInUser?, String>(
    save = { user -> if (user == null) emptyList() else listOf(user.firstName, user.role) },
    restore = { list -> if (list.isEmpty()) null else LoggedInUser(list[0], list[1]) }
)

@Composable
fun StudyHubApp(modifier: Modifier = Modifier) {
    var loggedInUser by rememberSaveable(stateSaver = LoggedInUserSaver) {
        mutableStateOf<LoggedInUser?>(null)
    }
    var showSignUp by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showCourses by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val user = loggedInUser
    when {
        user != null && user.role == "ADMIN" && showCourses -> CourseScreen(
            onBack = { showCourses = false },
            modifier = modifier
        )

        user != null && user.role == "ADMIN" && showCategories -> CategoryScreen(
            onBack = { showCategories = false },
            modifier = modifier
        )

        user != null && user.role == "ADMIN" -> AdminWelcomeScreen(
            user = user,
            onSignOut = { loggedInUser = null },
            onManageCategories = { showCategories = true },
            onManageCourses = { showCourses = true },
            modifier = modifier
        )

        user != null -> WelcomeScreen(
            user = user,
            onSignOut = { loggedInUser = null },
            modifier = modifier
        )

        showSignUp -> RegisterScreen(
            onRegistered = { showSignUp = false },
            onBack = { showSignUp = false },
            modifier = modifier
        )

        else -> LoginScreen(
            onLoginSuccess = { loggedInUser = it },
            onSignUpClick = { showSignUp = true },
            modifier = modifier
        )
    }
}