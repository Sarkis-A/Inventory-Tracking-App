package austin.sarkis.inventoryapp

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Activity that displays all groups the current user belongs to.
 *
 * Data model:
 * - Fan-out index: /users/{userId}/groups/{groupId} (role reference for each membership)
 * - Canonical group: /groups/{groupId} (contains metadata like name, description, ownerUid)
 *
 * Behavior:
 * - Listens to /users/{uid}/groups to discover memberships.
 * - For each group, attaches a listener on /groups/{groupId} to load metadata.
 * - Displays groups in a list. Clicking navigates to GroupInventoryActivity.
 */
enum class GroupRole { OWNER, ADMIN, MEMBER }

/** Represents a group row displayed in the user's list. */
data class GroupSummary(
    val id: String,
    val name: String,
    val description: String?,
    val role: GroupRole
)

class GroupListActivity : AppCompatActivity() {

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: GroupsAdapter

    // Firestore listeners
    private var userGroupsListenerRegistration: ListenerRegistration? = null
    private val groupDocumentListenerRegistrations = mutableMapOf<String, ListenerRegistration>()

    // In-memory map of groups keyed by groupId
    private val groups = mutableMapOf<String, GroupSummary>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.groups_list)

        // Back button
        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }

        // Recycler setup
        recycler = findViewById(R.id.recyclerGroups)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = GroupsAdapter { group ->
            // Open group inventory
            launchActivity<GroupInventoryActivity> {
                putExtra("groupId", group.id)
                putExtra("groupName", group.name)
                putExtra("role", group.role.name)
            }
        }
        recycler.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        ensureOwnerIndexIfMissing()
        attachUserGroupsListener()
    }

    override fun onStop() {
        super.onStop()
        detachAllListeners()
    }

    /**
     * Attaches a listener on the user's fan-out index at /users/{uid}/groups.
     * Each discovered groupId attaches a second listener to its canonical group document.
     */
    private fun attachUserGroupsListener() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "You must be signed in.", Toast.LENGTH_SHORT).show()
            return
        }

        userGroupsListenerRegistration?.remove()
        userGroupsListenerRegistration = firestore.collection("users").document(uid)
            .collection("groups")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val seenGroupIds = mutableSetOf<String>()

                for (userGroupDoc in snapshot.documents) {
                    val groupId = userGroupDoc.id
                    seenGroupIds += groupId

                    // Role is stored in index (default to MEMBER if missing)
                    val roleString = (userGroupDoc.getString("role") ?: "member").uppercase()
                    val role = runCatching { GroupRole.valueOf(roleString) }
                        .getOrDefault(GroupRole.MEMBER)

                    // Update role if it changed, else skip
                    if (groupDocumentListenerRegistrations.containsKey(groupId)) {
                        groups[groupId]?.let { existing ->
                            if (existing.role != role) {
                                groups[groupId] = existing.copy(role = role)
                                publishGroups()
                            }
                        }
                        continue
                    }

                    // Attach listener to the group document
                    val registration = firestore.collection("groups").document(groupId)
                        .addSnapshotListener { groupSnapshot, _ ->
                            if (groupSnapshot != null && groupSnapshot.exists()) {
                                val name = groupSnapshot.getString("name") ?: "Group"
                                val desc = groupSnapshot.getString("description")
                                groups[groupId] = GroupSummary(groupId, name, desc, role)
                                publishGroups()
                            } else {
                                groups.remove(groupId)
                                publishGroups()
                            }
                        }
                    groupDocumentListenerRegistrations[groupId] = registration
                }

                // Remove groups no longer in the index
                val removedGroupIds = groupDocumentListenerRegistrations.keys - seenGroupIds
                removedGroupIds.forEach { groupId ->
                    groupDocumentListenerRegistrations.remove(groupId)?.remove()
                    groups.remove(groupId)
                }
                if (removedGroupIds.isNotEmpty()) publishGroups()
            }
    }

    /**
     * Ensures that the owner's group is indexed under /users/{uid}/groups/{uid}.
     * This guarantees the owner sees their own group even if index creation was skipped.
     */
    private fun ensureOwnerIndexIfMissing() {
        val uid = auth.currentUser?.uid ?: return
        val groupDoc = firestore.collection("groups").document(uid)
        val userGroupIndex = firestore.collection("users").document(uid)
            .collection("groups").document(uid)

        userGroupIndex.get().addOnSuccessListener { indexDoc ->
            if (indexDoc.exists()) return@addOnSuccessListener
            groupDoc.get().addOnSuccessListener { group ->
                if (group.exists()) {
                    userGroupIndex.set(
                        mapOf(
                            "role" to "owner",
                            "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
            }
        }
    }

    /** Removes all Firestore listeners to prevent leaks. */
    private fun detachAllListeners() {
        userGroupsListenerRegistration?.remove()
        userGroupsListenerRegistration = null
        groupDocumentListenerRegistrations.values.forEach { it.remove() }
        groupDocumentListenerRegistrations.clear()
    }

    /**
     * Publishes the current group list to the adapter.
     * Owner groups appear first, followed by others alphabetically.
     */
    private fun publishGroups() {
        val sorted = groups.values.sortedWith(
            compareBy<GroupSummary> { if (it.role == GroupRole.OWNER) 0 else 1 }
                .thenBy { it.name.lowercase() }
        )
        adapter.submitList(sorted)
    }
}
