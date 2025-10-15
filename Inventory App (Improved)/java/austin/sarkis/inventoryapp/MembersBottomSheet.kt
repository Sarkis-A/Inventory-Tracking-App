package austin.sarkis.inventoryapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.widget.Button
import android.widget.LinearLayout
import com.google.android.material.textfield.TextInputEditText
import android.widget.Toast
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

/**
 * Bottom sheet UI for managing group members.
 *
 * Allows group owners to add, remove, or update member roles.
 * Non-owners can only view the member list.
 */
class MembersBottomSheet : BottomSheetDialogFragment() {

    private lateinit var groupId: String
    private var isOwner: Boolean = false

    private val db by lazy { FirebaseFirestore.getInstance() }
    private var firestoreListenerRegistration: ListenerRegistration? = null
    private lateinit var adapter: MembersAdapter

    companion object {
        /**
         * Factory method for creating a new bottom sheet instance.
         *
         * @param groupId Firestore group ID
         * @param isOwner true if current user is the group owner
         */
        fun new(groupId: String, isOwner: Boolean) = MembersBottomSheet().apply {
            arguments = Bundle().apply {
                putString("groupId", groupId)
                putBoolean("isOwner", isOwner)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = requireArguments().getString("groupId")!!
        isOwner = requireArguments().getBoolean("isOwner")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.members_sheet, container, false)
    }

    /**
     * Sets up the RecyclerView and add-member controls once the view is created.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val membersRecyclerView = view.findViewById<RecyclerView>(R.id.recyclerMembers)
        membersRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = MembersAdapter(
            isOwner = isOwner,
            onPromoteClicked = { member ->
                if (isOwner && member.role != "owner") {
                    updateMemberRole(member.userId, "admin")
                }
            },
            onDemoteClicked = { member ->
                if (isOwner && member.role != "owner") {
                    updateMemberRole(member.userId, "member")
                }
            },
            onRemoveClicked = { member ->
                if (isOwner && member.role != "owner") {
                    removeMember(member.userId)
                } else if (member.role == "owner") {
                    Toast.makeText(requireContext(), "Owner cannot be removed.", Toast.LENGTH_SHORT).show()
                }
            }
        )
        membersRecyclerView.adapter = adapter

        val addMemberLayout = view.findViewById<LinearLayout>(R.id.addMemberRow)
        val memberEmailInput = view.findViewById<TextInputEditText>(R.id.inputMemberEmail)
        val addMemberButton = view.findViewById<Button>(R.id.buttonAddMember)

        // Show add-member UI only for owners
        addMemberLayout.visibility = if (isOwner) View.VISIBLE else View.GONE

        addMemberButton.setOnClickListener {
            val email = memberEmailInput.text?.toString()?.trim().orEmpty()
            if (email.isEmpty()) {
                Toast.makeText(requireContext(), "Enter a member email.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            addMemberByEmail(email)
            memberEmailInput.setText("")
        }
    }

    override fun onStart() {
        super.onStart()
        // Live listener on group members collection
        firestoreListenerRegistration = db.collection("groups").document(groupId)
            .collection("members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val rows = snapshot?.documents?.mapNotNull { document ->
                    val userId = document.id
                    val role = document.getString("role") ?: "member"
                    val email = document.getString("email") ?: "[email missing]"
                    MemberRow(userId = userId, email = email, role = role)
                }.orEmpty()
                adapter.submitList(rows)
            }
    }

    override fun onStop() {
        super.onStop()
        firestoreListenerRegistration?.remove()
        firestoreListenerRegistration = null
    }

    // --- Firestore actions ---

    /**
     * Updates a member's role in both group membership and user index.
     */
    private fun updateMemberRole(userId: String, newRole: String) {
        val memberRef = db.collection("groups").document(groupId)
            .collection("members").document(userId)
        val indexRef = db.collection("users").document(userId)
            .collection("groups").document(groupId)

        db.runBatch { batch ->
            batch.set(memberRef, mapOf("role" to newRole), SetOptions.merge())
            batch.set(indexRef, mapOf("role" to newRole), SetOptions.merge())
        }
            .addOnSuccessListener { updateMemberRowLocal(userId, newRole) }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Failed to update role: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Removes a member from both group membership and user index.
     */
    private fun removeMember(userId: String) {
        val memberRef = db.collection("groups").document(groupId)
            .collection("members").document(userId)
        val indexRef = db.collection("users").document(userId)
            .collection("groups").document(groupId)

        db.runBatch { batch ->
            batch.delete(memberRef)
            batch.delete(indexRef)
        }
            .addOnSuccessListener { removeMemberRowLocal(userId) }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Failed to remove member: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- Local UI updates ---

    private fun updateMemberRowLocal(userId: String, newRole: String) {
        val currentList = adapter.currentList.toMutableList()
        val index = currentList.indexOfFirst { it.userId == userId }
        if (index >= 0) {
            currentList[index] = currentList[index].copy(role = newRole)
            adapter.submitList(currentList)
        }
    }

    private fun removeMemberRowLocal(userId: String) {
        val currentList = adapter.currentList.toMutableList()
        val index = currentList.indexOfFirst { it.userId == userId }
        if (index >= 0) {
            currentList.removeAt(index)
            adapter.submitList(currentList)
        }
    }

    private fun addMemberRowLocal(userId: String, email: String, role: String = "member") {
        val currentList = adapter.currentList.toMutableList()
        if (currentList.any { it.userId == userId }) return
        currentList.add(MemberRow(userId = userId, email = email, role = role))
        adapter.submitList(currentList)
    }

    /**
     * Looks up a user by email and adds them to the group.
     *
     * NOTE: This direct email→UID lookup is for development only.
     * In production, replace with a secure Cloud Function to avoid UID enumeration risk.
     */
    private fun addMemberByEmail(email: String) {
        val normalizedEmail = email.trim().lowercase()
        db.collection("userEmails").document(normalizedEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                val userId = snapshot.getString("userId")
                if (userId.isNullOrBlank()) {
                    Toast.makeText(requireContext(), "No user found for $normalizedEmail", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val memberRef = db.collection("groups").document(groupId)
                    .collection("members").document(userId)

                val memberData = mapOf(
                    "role" to "member",
                    "email" to normalizedEmail
                )

                memberRef.set(memberData)
                    .addOnSuccessListener {
                        addMemberRowLocal(userId, normalizedEmail, "member")

                        // Fan-out index for quicker group discovery
                        val indexRef = db.collection("users").document(userId)
                            .collection("groups").document(groupId)

                        val groupIndexData = mapOf(
                            "role" to "member",
                            "createdAt" to FieldValue.serverTimestamp()
                        )

                        indexRef.set(groupIndexData, SetOptions.merge())
                    }
                    .addOnFailureListener { exception ->
                        Toast.makeText(requireContext(), "Error adding member: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Lookup failed: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

/**
 * Data model representing a single member row in the group.
 */
data class MemberRow(
    val userId: String,   // Firestore key for membership doc
    val email: String,    // Email shown in UI
    val role: String      // "owner", "admin", or "member"
)
