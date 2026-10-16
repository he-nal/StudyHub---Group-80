package com.group80.studyhub

data class FieldErrors(
    val first: String? = null,
    val last: String? = null,
    val email: String? = null,
    val password: String? = null,
    val confirm: String? = null
) {
    val hasErrors: Boolean
        get() = listOf(first, last, email, password, confirm).any { it != null }
}

object AccountValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    private val NAME_REGEX = Regex("^[\\p{L}][\\p{L} '\\-]*$")

    fun validate(
        first: String, last: String, email: String,
        password: String, confirm: String
    ): FieldErrors = FieldErrors(
        first = nameError(first, "First name"),
        last = nameError(last, "Last name"),
        email = when {
            email.isBlank() -> "Email is required"
            !EMAIL_REGEX.matches(email.trim()) -> "Enter a valid email address"
            else -> null
        },
        password = when {
            password.length < 8 -> "Use at least 8 characters"
            password.none { it.isLetter() } -> "Include at least one letter"
            password.none { it.isDigit() } -> "Include at least one number"
            else -> null
        },
        confirm = if (confirm != password) "Passwords do not match" else null
    )

    private fun nameError(value: String, label: String): String? = when {
        value.isBlank() -> "$label is required"
        !NAME_REGEX.matches(value.trim()) -> "$label contains invalid characters"
        else -> null
    }
}