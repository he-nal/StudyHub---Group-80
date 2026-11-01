package com.group80.studyhub

//same as FieldErrors in AccountValidator.kt: null = field is fine, text = message to show.
data class CategoryFieldErrors(
    val name: String? = null,
    val description: String? = null
) {
    val hasErrors: Boolean
        get() = name != null || description != null
}

//field validation for the add-category form
object CategoryValidator {
    const val NAME_MIN = 2
    const val NAME_MAX = 50
    const val DESCRIPTION_MAX = 200

    //starts with letter/number, then letters, numbers, spaces and a few symbols ("C++ & Data (Intro)").
    private val NAME_REGEX = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} &'\\-,.:/()+#]*$")
    private val SPACES = Regex("\\s+")

    //trims and collapses repeated spaces: "  Computer   Science " -> "Computer Science".
    fun normalizeName(name: String): String = name.trim().replace(SPACES, " ")

    //names are the same when they only differ by capital letters or extra spaces.
    fun sameName(a: String, b: String): Boolean =
        normalizeName(a).equals(normalizeName(b), ignoreCase = true)

    /**
     * @param existingNames names already in the database, used to reject duplicates.
     *        For edit, leave out the category's own current name.
     */
    fun validate(
        name: String,
        description: String,
        existingNames: List<String>
    ): CategoryFieldErrors {
        val cleanName = normalizeName(name)
        val cleanDescription = description.trim()
        return CategoryFieldErrors(
            name = when {
                cleanName.isEmpty() -> "Category name is required"
                cleanName.length < NAME_MIN -> "Name must be at least $NAME_MIN characters"
                cleanName.length > NAME_MAX -> "Name must be $NAME_MAX characters or fewer"
                !NAME_REGEX.matches(cleanName) ->
                    "Start with a letter or number; use only letters, numbers, spaces and & ' - , . : / ( ) + #"
                existingNames.any { sameName(it, cleanName) } -> "A category with this name already exists"
                else -> null
            },
            description = when {
                cleanDescription.isEmpty() -> "Description is required"
                cleanDescription.length > DESCRIPTION_MAX ->
                    "Description must be $DESCRIPTION_MAX characters or fewer"
                else -> null
            }
        )
    }
}
