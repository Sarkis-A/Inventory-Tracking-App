package austin.sarkis.inventoryapp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Data model representing a single item in a group inventory.
 */
data class GroupItem(
    val id: String,
    val name: String,
    val quantity: Int,
    val description: String? = null
)

/**
 * Activity for displaying and managing a group inventory.
 *
 * Features:
 * - Paginates items for performance with large inventories.
 * - Listens to per-document updates for efficient real-time sync.
 * - Allows add, edit, and delete for OWNER/ADMIN roles.
 * - Provides owner access to the group members panel.
 */
class GroupInventoryActivity : AppCompatActivity() {

    private lateinit var role: GroupRole
    private lateinit var groupId: String
    private lateinit var groupName: String

    private lateinit var inventoryRecyclerView: RecyclerView
    private lateinit var adapter: GroupItemsAdapter

    private val db by lazy { FirebaseFirestore.getInstance() }

    // Pagination + live updates
    private val groupItems = mutableListOf<GroupItem>()
    private val perDocListeners = mutableMapOf<String, ListenerRegistration>()
    private var isLoadingPage = false
    private var reachedEnd = false
    private var lastVisible: DocumentSnapshot? = null

    private val pageSize = 50L
    private val prefetchThreshold = 5

    private lateinit var addEditItemLauncher: ActivityResultLauncher<Intent>

    /**
     * Sets up UI, reads group metadata, configures adapter,
     * and initializes paginated loading with per-document listeners.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        // Extract arguments passed to this activity
        groupId = intent.getStringExtra("groupId") ?: ""
        groupName = intent.getStringExtra("groupName") ?: getString(R.string.groups)
        role = intent.getStringExtra("role")?.let { GroupRole.valueOf(it) } ?: GroupRole.MEMBER

        // Title and back navigation
        findViewById<TextView>(R.id.textDashboardTitle)?.text = groupName
        findViewById<ImageButton>(R.id.imageButtonSettings)?.setOnClickListener {
            launchActivity<SettingsActivity>(
                finishCurrent = true,
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }

        // Recycler setup
        inventoryRecyclerView = findViewById(R.id.recyclerViewInventory)
        val layoutManager = LinearLayoutManager(this)
        inventoryRecyclerView.layoutManager = layoutManager

        val isUserPrivileged = role == GroupRole.OWNER || role == GroupRole.ADMIN

        // Launcher for add/edit results
        addEditItemLauncher = registerForActivityResult(StartActivityForResult()) { _ -> }

        adapter = GroupItemsAdapter(
            isUserPrivileged = isUserPrivileged,
            onEdit = { item -> launchEditItem(item) },
            onDelete = { item -> confirmAndDelete(item) }
        )
        inventoryRecyclerView.adapter = adapter

        // Enable endless scrolling
        attachEndlessScroll(layoutManager)

        // Floating Action Buttons
        // Add item button (only for owner/admin)
        val addItemButton = findViewById<View>(R.id.fabAdd)
        addItemButton.isVisible = isUserPrivileged
        addItemButton.setOnClickListener {
            val intent = intentFor<AddEditItemActivity> {
                putExtra(AddEditItemActivity.EXTRA_MODE, AddEditItemActivity.MODE_GROUP)
                putExtra(AddEditItemActivity.EXTRA_GROUP_ID, groupId)
            }
            addEditItemLauncher.launch(intent)
        }

        // Manage members button (owner only)
        val groupMembersButton = findViewById<FloatingActionButton>(R.id.fabMembers)
        groupMembersButton.visibility = if (role == GroupRole.OWNER) View.VISIBLE else View.GONE
        groupMembersButton.setOnClickListener {
            MembersBottomSheet.new(groupId = groupId, isOwner = (role == GroupRole.OWNER))
                .show(supportFragmentManager, "members_sheet")
        }
    }

    /**
     * Refreshes the group items list when activity starts.
     */
    override fun onStart() {
        super.onStart()
        resetAndLoadFirstPage()
    }

    /**
     * Cleans up Firestore listeners when activity stops.
     */
    override fun onStop() {
        super.onStop()
        perDocListeners.values.forEach { it.remove() }
        perDocListeners.clear()
    }

    /**
     * Adds endless scroll behavior to the RecyclerView.
     * Loads next page when the user scrolls near the end.
     */
    private fun attachEndlessScroll(layoutManager: LinearLayoutManager) {
        inventoryRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0) return
                val lastPos = layoutManager.findLastVisibleItemPosition()
                if (!isLoadingPage && !reachedEnd && lastPos >= groupItems.size - prefetchThreshold) {
                    loadNextPage()
                }
            }
        })
    }

    /**
     * Clears the current list and loads the first page of items.
     */
    private fun resetAndLoadFirstPage() {
        if (groupId.isBlank()) {
            Toast.makeText(this, "Missing groupId.", Toast.LENGTH_SHORT).show()
            return
        }
        groupItems.clear()
        adapter.submitList(groupItems.toList())
        perDocListeners.values.forEach { it.remove() }
        perDocListeners.clear()
        reachedEnd = false
        lastVisible = null
        loadNextPage()
    }

    /**
     * Loads the next page of items from Firestore,
     * then attaches per-document listeners for live updates.
     */
    private fun loadNextPage() {
        if (isLoadingPage || reachedEnd) return
        isLoadingPage = true

        var query = db.collection("groups").document(groupId)
            .collection("items")
            .orderBy("name")

        if (lastVisible != null) query = query.startAfter(lastVisible!!)
        query.limit(pageSize)
            .get()
            .addOnSuccessListener { snapshot ->
                val documents = snapshot.documents
                if (documents.isEmpty()) {
                    reachedEnd = true
                    isLoadingPage = false
                    return@addOnSuccessListener
                }

                // Append new items to list
                for (doc in documents) {
                    val name = doc.getString("name").orEmpty()
                    val quantity = (doc.getLong("quantity") ?: 0L).toInt()
                    val description = doc.getString("description")
                    groupItems.add(GroupItem(doc.id, name, quantity, description))
                }

                adapter.submitList(groupItems.toList())

                // Update pagination cursor
                lastVisible = documents.last()

                // Attach listeners for live updates
                attachPerDocListeners(documents)

                isLoadingPage = false
            }
            .addOnFailureListener { error ->
                isLoadingPage = false
                Toast.makeText(this, "Failed to load items: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Attaches snapshot listeners for each item in the given list
     * so only changed rows update in place.
     */
    private fun attachPerDocListeners(documentSnapshots: List<DocumentSnapshot>) {
        for (doc in documentSnapshots) {
            val id = doc.id
            if (perDocListeners.containsKey(id)) continue

            val ref: DocumentReference = db.collection("groups").document(groupId)
                .collection("items").document(id)

            val registration = ref.addSnapshotListener { snapshot, _ ->
                val index = groupItems.indexOfFirst { it.id == id }

                if (snapshot == null || !snapshot.exists()) {
                    // Item deleted
                    if (index >= 0) {
                        groupItems.removeAt(index)
                        adapter.submitList(groupItems.toList())
                    }
                    perDocListeners.remove(id)?.remove()
                } else {
                    // Item updated or inserted
                    val updatedItem = GroupItem(
                        id = snapshot.id,
                        name = snapshot.getString("name").orEmpty(),
                        quantity = (snapshot.getLong("quantity") ?: 0L).toInt(),
                        description = snapshot.getString("description")
                    )
                    if (index >= 0) {
                        groupItems[index] = updatedItem
                    } else {
                        groupItems.add(updatedItem)
                    }
                    adapter.submitList(groupItems.toList())
                }
            }
            perDocListeners[id] = registration
        }
    }

    /**
     * Opens the editor for a given item in group mode.
     */
    private fun launchEditItem(item: GroupItem) {
        val intent = intentFor<AddEditItemActivity> {
            putExtra(AddEditItemActivity.EXTRA_MODE, AddEditItemActivity.MODE_GROUP)
            putExtra(AddEditItemActivity.EXTRA_GROUP_ID, groupId)
            putExtra(AddEditItemActivity.EXTRA_ITEM_ID, item.id)
            putExtra(AddEditItemActivity.EXTRA_NAME, item.name)
            putExtra(AddEditItemActivity.EXTRA_DESC, item.description)
            putExtra(AddEditItemActivity.EXTRA_QUANTITY, item.quantity)
        }
        addEditItemLauncher.launch(intent)
    }

    /**
     * Confirms deletion with a dialog and deletes
     * the item from Firestore if confirmed.
     */
    private fun confirmAndDelete(item: GroupItem) {
        val view = layoutInflater.inflate(R.layout.confirm_delete_item, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setCancelable(true)
            .create()

        val btnConfirm = view.findViewById<Button>(R.id.buttonDeleteConfirm)
        val btnCancel = view.findViewById<Button>(R.id.buttonDeleteCancel)

        btnConfirm.setOnClickListener {
            db.collection("groups").document(groupId)
                .collection("items").document(item.id)
                .delete()
                .addOnSuccessListener {
                    dialog.dismiss()
                    // Row removed via snapshot listener
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "Delete failed: ${error.message}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}

/**
 * RecyclerView adapter for displaying group inventory items.
 * Edit/Delete buttons are hidden if the user lacks privileges.
 */
private class GroupItemsAdapter(
    private val isUserPrivileged: Boolean,
    private val onEdit: (GroupItem) -> Unit,
    private val onDelete: (GroupItem) -> Unit
) : ListAdapter<GroupItem, GroupItemsAdapter.VH>(Diff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.inventory_item, parent, false)
        return VH(view, isUserPrivileged, onEdit, onDelete)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(
        itemView: View,
        private val isUserPrivileged: Boolean,
        private val onEdit: (GroupItem) -> Unit,
        private val onDelete: (GroupItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val name = itemView.findViewById<TextView>(R.id.textItemName)
        private val quantity = itemView.findViewById<TextView>(R.id.textItemQuantity)
        private val btnEdit = itemView.findViewById<Button>(R.id.buttonEditItem)
        private val btnDelete = itemView.findViewById<Button>(R.id.buttonDeleteItem)

        fun bind(item: GroupItem) {
            name.text = item.name
            quantity.text = itemView.context.getString(R.string.quantity, item.quantity)

            btnEdit.isVisible = isUserPrivileged
            btnDelete.isVisible = isUserPrivileged

            if (isUserPrivileged) {
                btnEdit.setOnClickListener { onEdit(item) }
                btnDelete.setOnClickListener { onDelete(item) }
            } else {
                btnEdit.setOnClickListener(null)
                btnDelete.setOnClickListener(null)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<GroupItem>() {
        override fun areItemsTheSame(oldItem: GroupItem, newItem: GroupItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: GroupItem, newItem: GroupItem) = oldItem == newItem
    }
}
