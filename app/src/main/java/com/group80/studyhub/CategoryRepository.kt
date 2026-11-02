package com.group80.studyhub

import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

class DuplicateCategoryException : Exception("A category with this name already exists")

//Reads and writes categories/{id}/{name, description} in the Realtime Database.
object CategoryRepository {
    private val categories = FirebaseDatabase.getInstance().reference.child("categories")

    suspend fun fetchAll(): Result<List<Category>> = try {
        val snapshot = categories.get().await()
        val list = snapshot.children.map { child ->
            Category(
                id = child.key.orEmpty(),
                name = child.child("name").getValue(String::class.java).orEmpty(),
                description = child.child("description").getValue(String::class.java).orEmpty()
            )
        }.sortedBy { it.name.lowercase() }
        Result.success(list)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    //cleans input, rejects a duplicate name, then saves. Call CategoryValidator first for field messages.
    suspend fun add(name: String, description: String): Result<Category> = try {
        val cleanName = CategoryValidator.normalizeName(name)
        val cleanDescription = description.trim()

        //new read (not the list on screen, which may be old) so two admins can't create the same name.
        val existing = fetchAll().getOrThrow()
        if (existing.any { CategoryValidator.sameName(it.name, cleanName) }) {
            throw DuplicateCategoryException()
        }

        val ref = categories.push()
        ref.setValue(mapOf("name" to cleanName, "description" to cleanDescription)).await()
        Result.success(Category(ref.key.orEmpty(), cleanName, cleanDescription))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    //same checks as add(), but keeps the id and saves over the existing category.
    suspend fun update(id: String, name: String, description: String): Result<Category> = try {
        require(id.isNotBlank()) { "Missing category id" }
        val cleanName = CategoryValidator.normalizeName(name)
        val cleanDescription = description.trim()

        //new read, like add(): the category may have been deleted, or its new name taken, since the list loaded.
        val existing = fetchAll().getOrThrow()
        if (existing.none { it.id == id }) {
            throw IllegalStateException("This category no longer exists")
        }
        //skip the category itself, so keeping the same name isn't a "duplicate".
        if (existing.any { it.id != id && CategoryValidator.sameName(it.name, cleanName) }) {
            throw DuplicateCategoryException()
        }

        //updateChildren only changes these two fields; anything else stored on the category is kept.
        categories.child(id).updateChildren(mapOf("name" to cleanName, "description" to cleanDescription)).await()
        Result.success(Category(id, cleanName, cleanDescription))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun delete(id: String): Result<Unit> = try {
        require(id.isNotBlank()) { "Missing category id" }
        categories.child(id).removeValue().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
