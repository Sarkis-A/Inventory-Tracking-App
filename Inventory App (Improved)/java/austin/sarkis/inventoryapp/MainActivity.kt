package austin.sarkis.inventoryapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

/**
 * Main screen that shows the user's inventory list.
 *
 * Modes:
 * - Guest mode: items are stored locally in SQLite.
 * - Cloud mode: items are paginated from Firestore with live per-document updates.
 */
class MainActivity : AppCompatActivity() {

    // Recycler + adapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var cloudAdapter: CloudItemAdapter

    // Local database (guest mode)
    private lateinit var localDb: LocalInventoryDatabase

    // Add/Edit navigation
    private lateinit var addEditItemLauncher: ActivityResultLauncher<Intent>

    // Mode detection
    private var isGuest: Boolean = false

    // Firebase (cloud mode)
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // Backing list for cloud items
    private val cloudItems = mutableListOf<CloudItem>()

    // Firestore document listeners for cloud items
    private val perDocListeners = mutableMapOf<String, ListenerRegistration>()

    // Paging state
    private var isLoadingPage = false
    private var reachedEnd = false
    private var lastVisible: DocumentSnapshot? = null

    // Paging constants
    private val pageSize = 50L
    private val prefetchThreshold = 5

    /**
     * Entry point: sets up UI, determines guest vs. cloud mode, and starts data loading.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        // Recycler setup
        recyclerView = findViewById(R.id.recyclerViewInventory)
        val layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager

        // Settings button
        findViewById<ImageButton>(R.id.imageButtonSettings).setOnClickListener {
            launchActivity<SettingsActivity>()
        }

        // Floating action button (add item)
        val addItemFab: FloatingActionButton = findViewById(R.id.fabAdd)

        // Local DB for guest mode
        localDb = LocalInventoryDatabase(this)

        // Detect mode
        val appPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        isGuest = appPreferences.getBoolean("is_guest", false)
        auth.currentUser?.let { isGuest = false }

        // Register launcher for Add/Edit screen
        addEditItemLauncher = registerForActivityResult(StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                if (isGuest) {
                    // Reload local DB and check for low stock
                    loadLocalItems()
                    notifyLowStockItems()
                } else {
                    // Refresh top page so new cloud item appears
                    resetAndLoadFirstPage()
                }
            }
        }

        // FAB action for add
        addItemFab.setOnClickListener {
            val intent = intentFor<AddEditItemActivity>().apply {
                putExtra(
                    AddEditItemActivity.EXTRA_MODE,
                    if (isGuest) AddEditItemActivity.MODE_GUEST else AddEditItemActivity.MODE_CLOUD
                )
            }
            addEditItemLauncher.launch(intent)
        }

        // Initialize based on mode
        if (isGuest) {
            loadLocalItems()
            promptSmsPermissionIfNeeded()
        } else {
            setupCloudAdapter()
            attachEndlessScroll(layoutManager)
            resetAndLoadFirstPage()
        }
    }

    /**
     * Sets up cloud adapter with edit and delete handlers.
     */
    private fun setupCloudAdapter() {
        cloudAdapter = CloudItemAdapter(
            onEditItem = { item -> editCloudItem(item) },
            onDeleteItem = { item -> deleteCloudItem(item) }
        )
        recyclerView.adapter = cloudAdapter
    }

    /**
     * Endless scroll listener to fetch the next page when close to end.
     */
    private fun attachEndlessScroll(layoutManager: LinearLayoutManager) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(rv, dx, dy)
                if (dy <= 0) return
                val lastPos = layoutManager.findLastVisibleItemPosition()
                val nearEnd = lastPos >= cloudItems.size - prefetchThreshold
                if (!isLoadingPage && !reachedEnd && nearEnd) {
                    loadNextPage()
                }
            }
        })
    }

    /**
     * Launches Add/Edit activity prefilled for a cloud item.
     */
    private fun editCloudItem(item: CloudItem) {
        val intent = intentFor<AddEditItemActivity>().apply {
            putExtra(AddEditItemActivity.EXTRA_MODE, AddEditItemActivity.MODE_CLOUD)
            putExtra(AddEditItemActivity.EXTRA_ITEM_ID, item.id)
            putExtra(AddEditItemActivity.EXTRA_NAME, item.name)
            putExtra(AddEditItemActivity.EXTRA_DESC, item.description)
            putExtra(AddEditItemActivity.EXTRA_QUANTITY, item.quantity)
        }
        addEditItemLauncher.launch(intent)
    }

    /**
     * Shows confirmation dialog and deletes a cloud item.
     */
    private fun deleteCloudItem(item: CloudItem) {
        val dialogView = layoutInflater.inflate(R.layout.confirm_delete_item, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val confirmButton = dialogView.findViewById<Button>(R.id.buttonDeleteConfirm)
        val cancelButton = dialogView.findViewById<Button>(R.id.buttonDeleteCancel)

        confirmButton.setOnClickListener {
            val currentUid = auth.currentUser?.uid
            if (currentUid != null) {
                firestore.collection("users").document(currentUid)
                    .collection("items").document(item.id)
                    .delete()
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Delete failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
            }
            dialog.dismiss()
        }
        cancelButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /**
     * Resets paging state and reloads the first page.
     */
    private fun resetAndLoadFirstPage() {
        cloudItems.clear()
        cloudAdapter.submitList(emptyList())
        perDocListeners.values.forEach { it.remove() }
        perDocListeners.clear()
        reachedEnd = false
        lastVisible = null
        loadNextPage()
    }

    /**
     * Loads the next page of items from Firestore.
     */
    private fun loadNextPage() {
        val uid = auth.currentUser?.uid ?: return
        isLoadingPage = true

        var query = firestore.collection("users").document(uid)
            .collection("items")
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        if (lastVisible != null) query = query.startAfter(lastVisible!!)
        query.limit(pageSize).get()
            .addOnSuccessListener { snap ->
                val docs = snap.documents
                if (docs.isEmpty()) {
                    reachedEnd = true
                    isLoadingPage = false
                    return@addOnSuccessListener
                }

                val newModels = docs.map { d ->
                    CloudItem(
                        id = d.id,
                        name = d.getString("name").orEmpty(),
                        description = d.getString("description"),
                        quantity = (d.getLong("quantity") ?: 0L).toInt()
                    )
                }
                cloudItems.addAll(newModels)
                cloudAdapter.submitList(cloudItems.toList())

                lastVisible = docs.last()
                attachPerDocListeners(uid, docs)
                isLoadingPage = false
            }
            .addOnFailureListener {
                isLoadingPage = false
                Toast.makeText(this, "Failed to load items.", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Attaches real-time listeners so only changed items update in place.
     */
    private fun attachPerDocListeners(uid: String, docs: List<DocumentSnapshot>) {
        for (doc in docs) {
            val id = doc.id
            if (perDocListeners.containsKey(id)) continue

            val ref: DocumentReference = firestore.collection("users").document(uid)
                .collection("items").document(id)

            val reg = ref.addSnapshotListener { d, e ->
                if (e != null || d == null) return@addSnapshotListener
                if (!d.exists()) {
                    val idx = cloudItems.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        cloudItems.removeAt(idx)
                        cloudAdapter.submitList(cloudItems.toList())
                    }
                    perDocListeners.remove(id)?.remove()
                } else {
                    val updated = CloudItem(
                        id = d.id,
                        name = d.getString("name").orEmpty(),
                        description = d.getString("description"),
                        quantity = (d.getLong("quantity") ?: 0L).toInt()
                    )
                    val idx = cloudItems.indexOfFirst { it.id == id }
                    if (idx >= 0) cloudItems[idx] = updated else cloudItems.add(updated)
                    cloudAdapter.submitList(cloudItems.toList())
                }
            }
            perDocListeners[id] = reg
        }
    }

    /**
     * Loads items from local database in guest mode.
     */
    private fun loadLocalItems() {
        if (!isGuest) return
        val itemList = localDb.allInventoryItemsList
        val adapter = LocalItemAdapter(itemList, localDb, addEditItemLauncher)
        recyclerView.adapter = adapter
    }

    /**
     * Shows one-time SMS permission rationale (guest mode).
     */
    private fun promptSmsPermissionIfNeeded() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val shown = prefs.getBoolean("sms_dialog_shown", false)
        if (!shown) {
            showSmsPermissionDialog()
            prefs.edit { putBoolean("sms_dialog_shown", true) }
        }
    }

    /**
     * Displays SMS permission dialog.
     */
    private fun showSmsPermissionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.sms_permission_activity, null)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val grantButton = dialogView.findViewById<Button>(R.id.buttonSmsGrant)
        val denyButton = dialogView.findViewById<Button>(R.id.buttonSmsDeny)

        grantButton.setOnClickListener {
            dialog.dismiss()
            launchActivity<SettingsActivity>()
        }
        denyButton.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "SMS permission denied.", Toast.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    /**
     * Returns true if SEND_SMS permission is granted.
     */
    private fun hasSmsSendPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sends SMS alert for low stock.
     */
    private fun sendLowStockSms(message: String) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val phone = prefs.getString("sms_phone_number", "") ?: ""
        if (phone.isBlank()) {
            Toast.makeText(this, "No phone number set for SMS notifications.", Toast.LENGTH_SHORT).show()
            return
        }
        val sms: SmsManager = systemService()
        sms.sendTextMessage(phone, null, message, null, null)
    }

    /**
     * Sends SMS alerts for items with low stock in guest mode.
     */
    private fun notifyLowStockItems() {
        if (!hasSmsSendPermission()) {
            Toast.makeText(this, "SMS permission not granted. Cannot send notifications.", Toast.LENGTH_SHORT).show()
            return
        }
        val items = localDb.allInventoryItemsList
        for (item in items) {
            if (item.quantity <= 5) {
                if (!isItemAlreadyNotified(item.id)) {
                    sendLowStockSms("Your inventory is low for: ${item.name}")
                    markItemNotified(item.id, true)
                }
            } else {
                markItemNotified(item.id, false)
            }
        }
    }

    /**
     * Returns true if the item has already triggered a notification.
     */
    private fun isItemAlreadyNotified(itemId: Long): Boolean {
        return getSharedPreferences("sms_notified_items", MODE_PRIVATE)
            .getBoolean("notified_$itemId", false)
    }

    /**
     * Marks whether an item has triggered a low-stock notification.
     */
    private fun markItemNotified(itemId: Long, notified: Boolean) {
        getSharedPreferences("sms_notified_items", MODE_PRIVATE)
            .edit { putBoolean("notified_$itemId", notified) }
    }

    /**
     * Cleans up Firestore listeners on destroy.
     */
    override fun onDestroy() {
        super.onDestroy()
        perDocListeners.values.forEach { it.remove() }
        perDocListeners.clear()
    }
}
