package austin.sarkis.inventoryapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Create a new inventory item or edit an existing one.
 *
 * Supported persistence modes (via [EXTRA_MODE]):
 * - [MODE_GUEST]: Local SQLite (guest mode).
 * - [MODE_CLOUD]: Firestore under the signed-in user's `/users/{uid}/items`.
 * - [MODE_GROUP]: Firestore under a group `/groups/{groupId}/items`.
 *
 * The screen toggles between **Add** (create) and **Save** (edit) based on the supplied extras.
 */
class AddEditItemActivity : AppCompatActivity() {

    /** Intent keys and mode constants. */
    companion object {
        /** Which persistence mode to use: [MODE_GUEST], [MODE_CLOUD], or [MODE_GROUP]. */
        const val EXTRA_MODE = "persistenceMode"

        /** Local SQLite via [LocalInventoryDatabase]. */
        const val MODE_GUEST = "guest"

        /** Firestore under the authenticated user’s document. */
        const val MODE_CLOUD = "cloud"

        /** Firestore under a group document. */
        const val MODE_GROUP = "group"

        /** Remote document id (Firestore) when editing. */
        const val EXTRA_ITEM_ID = "itemId"

        /** Optional prefill: item name. */
        const val EXTRA_NAME = "name"

        /** Optional prefill: item description. */
        const val EXTRA_DESC = "description"

        /** Optional prefill: item quantity. */
        const val EXTRA_QUANTITY = "quantity"

        /** Required for [MODE_GROUP]: the group id. */
        const val EXTRA_GROUP_ID = "groupId"
    }

    // Views
    private lateinit var nameInput: EditText
    private lateinit var descriptionInput: EditText
    private lateinit var quantityInput: EditText
    private lateinit var incrementButton: Button
    private lateinit var decrementButton: Button
    private lateinit var saveButton: Button
    private lateinit var addButton: Button
    private lateinit var backButton: ImageButton

    // Local (guest) state
    private var localInventoryDatabase: LocalInventoryDatabase? = null
    private var localItemId: Long = -1L // > 0 when editing a local row

    // Mode / context
    private var mode: String = MODE_GUEST
    private var isEditing: Boolean = false

    // Cloud / group state
    private val firebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var remoteItemId: String? = null
    private var groupId: String? = null

    /**
     * Bind views, derive mode and edit state from extras, prefill inputs, and wire actions.
     */
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.add_edit_item)

        // Bind
        nameInput = findViewById(R.id.inputItemName)
        descriptionInput = findViewById(R.id.inputItemDescription)
        quantityInput = findViewById(R.id.inputQuantity)
        incrementButton = findViewById(R.id.buttonQuantityIncrement)
        decrementButton = findViewById(R.id.buttonQuantityDecrement)
        saveButton = findViewById(R.id.buttonSaveItem)
        addButton = findViewById(R.id.buttonAddItem)
        backButton = findViewById(R.id.buttonBack)

        // Determine mode + edit state
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_GUEST
        when (mode) {
            MODE_GUEST -> {
                localInventoryDatabase = LocalInventoryDatabase(this)
                // Optional: local row id if caller is editing a local entry
                localItemId = intent.getLongExtra("localItemId", -1L)
                isEditing = localItemId > 0
            }
            MODE_CLOUD -> {
                remoteItemId = intent.getStringExtra(EXTRA_ITEM_ID)
                isEditing = !remoteItemId.isNullOrEmpty()
            }
            MODE_GROUP -> {
                groupId = intent.getStringExtra(EXTRA_GROUP_ID)
                remoteItemId = intent.getStringExtra(EXTRA_ITEM_ID)
                isEditing = !remoteItemId.isNullOrEmpty()
            }
            else -> {
                // Fallback to guest if unrecognized
                localInventoryDatabase = LocalInventoryDatabase(this)
                localItemId = -1L
                isEditing = false
            }
        }

        // Prefill (used for edit flows)
        nameInput.setText(intent.getStringExtra(EXTRA_NAME) ?: "")
        descriptionInput.setText(intent.getStringExtra(EXTRA_DESC) ?: "")
        quantityInput.setText((intent.getIntExtra(EXTRA_QUANTITY, 0)).toString())

        // Quantity controls (coerce to non-negative)
        incrementButton.setOnClickListener {
            val q = quantityInput.text.toString().toIntOrNull() ?: 0
            quantityInput.setText((q + 1).toString())
        }
        decrementButton.setOnClickListener {
            val q = quantityInput.text.toString().toIntOrNull() ?: 0
            quantityInput.setText((q - 1).coerceAtLeast(0).toString())
        }

        // Primary actions (enable one, dim the other)
        updateActionButtons(isEditing)
        addButton.setOnClickListener { if (!isEditing) saveItem() }
        saveButton.setOnClickListener { if (isEditing) saveItem() }

        // Back nav
        backButton.setOnClickListener { finish() }
    }

    /**
     * Enable/disable Add/Save buttons to reflect create vs edit state.
     */
    private fun updateActionButtons(isEditing: Boolean) {
        addButton.isEnabled = !isEditing
        saveButton.isEnabled = isEditing
        addButton.alpha = if (!isEditing) 1f else 0.4f
        saveButton.alpha = if (isEditing) 1f else 0.4f
    }

    /**
     * Validate input and route persistence to the selected mode.
     */
    private fun saveItem() {
        val name = nameInput.text.toString().trim()
        val description = descriptionInput.text.toString().trim().ifEmpty { null }
        val quantity = (quantityInput.text.toString().toIntOrNull() ?: 0).coerceAtLeast(0)

        if (name.isEmpty()) {
            Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show()
            return
        }

        when (mode) {
            MODE_GUEST -> saveLocal(name, description, quantity)
            MODE_GROUP -> saveGroup(name, description, quantity)
            else -> saveCloud(name, description, quantity) // MODE_CLOUD
        }
    }

    /**
     * Insert or update an item in the local SQLite database.
     *
     * Calls [setResult] and finishes on success.
     */
    private fun saveLocal(name: String, description: String?, quantity: Int) {
        if (isEditing && localItemId > 0) {
            localInventoryDatabase?.updateItem(localItemId, name, description ?: "", quantity)
        } else {
            localInventoryDatabase?.insertItem(name, description ?: "", quantity)
        }
        setResult(RESULT_OK)
        finish()
    }

    /**
     * Insert or update an item in Firestore at `/users/{uid}/items`.
     *
     * Adds `updatedAt` server timestamp to support list ordering.
     */
    private fun saveCloud(name: String, description: String?, quantity: Int) {
        val userId = firebaseAuth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Not signed in.", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf(
            "name" to name,
            "description" to description,
            "quantity" to quantity,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val items = firestore.collection("users").document(userId).collection("items")
        val task = if (isEditing && !remoteItemId.isNullOrEmpty()) {
            items.document(remoteItemId!!).update(data as Map<String, Any?>)
        } else {
            items.add(data).continueWith { }
        }

        task.addOnSuccessListener {
            setResult(RESULT_OK)
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to save item.", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Insert or update an item in Firestore at `/groups/{groupId}/items`.
     *
     * Requires a valid [groupId]. Group write permissions are enforced by rules.
     */
    private fun saveGroup(name: String, description: String?, quantity: Int) {
        val gid = groupId
        if (gid.isNullOrBlank()) {
            Toast.makeText(this, "Missing groupId.", Toast.LENGTH_SHORT).show()
            return
        }
        if (firebaseAuth.currentUser?.uid == null) {
            Toast.makeText(this, "Not signed in.", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf(
            "name" to name,
            "description" to description,
            "quantity" to quantity,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val items = firestore.collection("groups").document(gid).collection("items")
        val task = if (isEditing && !remoteItemId.isNullOrEmpty()) {
            items.document(remoteItemId!!).update(data as Map<String, Any?>)
        } else {
            items.add(data).continueWith { }
        }

        task.addOnSuccessListener {
            setResult(RESULT_OK)
            finish()
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to save item.", Toast.LENGTH_SHORT).show()
        }
    }
}
