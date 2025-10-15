package austin.sarkis.inventoryapp

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

/**
 * Deletes a group and all related data from Firestore.
 *
 * This includes:
 * - Items in `/groups/{groupId}/items`
 * - Members in `/groups/{groupId}/members`
 * - Each member's fan-out index at `/users/{memberUid}/groups/{groupId}`
 * - The owner’s fan-out index
 * - The group document itself
 *
 * Deletions are processed in batches to stay within Firestore’s
 * 500-operations-per-batch limit.
 *
 * @param groupId Firestore group document ID
 * @return true if deletion completed successfully, false otherwise
 */
suspend fun deleteGroupDeepAsync(groupId: String): Boolean = withContext(Dispatchers.IO) {
    val firestore = FirebaseFirestore.getInstance()
    val groupRef = firestore.collection("groups").document(groupId)

    // Verify group exists and capture owner UID
    val groupSnapshot = try {
        groupRef.get().await()
    } catch (_: Exception) {
        return@withContext false
    }
    if (!groupSnapshot.exists()) return@withContext true
    val ownerUid = groupSnapshot.getString("ownerUid") ?: return@withContext false

    // Batch helpers
    var batch: WriteBatch = firestore.batch()
    var operationCount = 0

    /** Commits the current batch and resets counters. */
    suspend fun flush() {
        if (operationCount > 0) {
            batch.commit().await()
            batch = firestore.batch()
            operationCount = 0
        }
    }

    /** Queues a document for deletion, flushing if batch is near capacity. */
    suspend fun deleteRef(ref: DocumentReference) {
        batch.delete(ref)
        operationCount++
        if (operationCount >= 450) flush()
    }

    /** Retrieves all documents in a collection using pagination. */
    suspend fun fetchAll(query: Query, pageSize: Long = 500L): List<DocumentSnapshot> {
        val documents = mutableListOf<DocumentSnapshot>()
        var last: DocumentSnapshot? = null
        while (true) {
            val paginatedQuery = if (last != null) query.startAfter(last) else query
            val snapshot = paginatedQuery.limit(pageSize).get().await()
            if (snapshot.isEmpty) break
            documents += snapshot.documents
            last = snapshot.documents.last()
        }
        return documents
    }

    try {
        // Delete all items
        val items = fetchAll(groupRef.collection("items").orderBy("__name__"))
        for (doc in items) deleteRef(doc.reference)
        flush()

        // Delete all members and their fan-out indexes
        val members = fetchAll(groupRef.collection("members").orderBy("__name__"))
        for (doc in members) {
            val memberUid = doc.id
            deleteRef(doc.reference)
            val indexReference = firestore.collection("users").document(memberUid)
                .collection("groups").document(groupId)
            deleteRef(indexReference)
        }
        flush()

        // Delete owner's fan-out index
        val ownerIndexReference = firestore.collection("users").document(ownerUid)
            .collection("groups").document(groupId)
        deleteRef(ownerIndexReference)
        flush()

        // Delete group document
        deleteRef(groupRef)
        flush()

        true
    } catch (_: Exception) {
        false
    }
}
