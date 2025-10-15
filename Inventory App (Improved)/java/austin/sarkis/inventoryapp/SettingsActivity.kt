package austin.sarkis.inventoryapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * Activity for managing app settings such as SMS alerts,
 * phone number, account, and group ownership actions.
 */
class SettingsActivity : AppCompatActivity() {

    // UI
    private lateinit var adapter: SettingsAdapter

    // SharedPreferences
    private val sharedPreferences by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }

    // Firebase
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private var ownerGroupListener: ListenerRegistration? = null

    // Tracks if the system permission dialog is up (prevents UI flicker)
    private var smsRequestInFlight = false

    // Permission request launcher for SEND_SMS
    private val requestSmsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        smsRequestInFlight = false
        syncSmsPermissionFromSystem()
        toast(if (hasSmsPermission()) "SMS permission granted." else "SMS permission denied.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)

        findViewById<ImageButton>(R.id.buttonBack).setOnClickListener { finish() }

        val settingsRecyclerView = findViewById<RecyclerView>(R.id.recyclerSettings)
        settingsRecyclerView.layoutManager = LinearLayoutManager(this)
        settingsRecyclerView.setHasFixedSize(false)
        settingsRecyclerView.addItemDecoration(
            VerticalSpaceDecoration(resources.getDimensionPixelSize(R.dimen.spacing_16))
        )

        adapter = SettingsAdapter(
            onSavePhone = { phoneNumber ->
                // Persist silently; avoid resetting EditText caret position
                savePhoneNumber(phoneNumber)
            },
            onSmsToggled = ::onSmsToggled,
            onLogout = ::handleLogout,
            onOpenOrCreateMyGroup = ::createOrOpenOwnerGroup,
            onViewMyGroups = {
                launchActivity<GroupListActivity>()
            },
            onDeleteMyGroup = ::confirmDeleteOwnedGroup
        )
        settingsRecyclerView.adapter = adapter

        // Tap-off unfocus handler (dismiss keyboard and save phone number)
        findViewById<View>(android.R.id.content).setOnTouchListener { view, motionEvent ->
            if (motionEvent.action == android.view.MotionEvent.ACTION_DOWN) {
                clearFocusAndMaybeSavePhone()
                view.performClick() // accessibility
            }
            false
        }

        // Build initial rows and sync SMS permission
        rebuildRows(phone = getPhoneNumber(), smsEnabled = isSmsEnabled(), locked = false)
        syncSmsPermissionFromSystem()
    }

    override fun onStart() {
        super.onStart()
        attachOwnerGroupListener()
    }

    override fun onStop() {
        super.onStop()
        ownerGroupListener?.remove()
        ownerGroupListener = null
    }

    override fun onResume() {
        super.onResume()
        smsRequestInFlight = false
        syncSmsPermissionFromSystem()
    }

    override fun onPause() {
        super.onPause()
        clearFocusAndMaybeSavePhone()
    }

    /**
     * Toggle SMS alerts. If enabling, request permission if needed.
     */
    private fun onSmsToggled(isEnabled: Boolean) {
        if (isEnabled) {
            if (hasSmsPermission()) {
                saveSmsEnabled(true)
                safeRebuildRows(smsEnabled = true, locked = false)
            } else {
                smsRequestInFlight = true
                safeRebuildRows(smsEnabled = false, locked = true)
                requestSmsPermission.launch(Manifest.permission.SEND_SMS)
            }
        } else {
            // Direct user to revoke permission in system settings
            openAppSettings()
        }
    }

    /**
     * Sync UI with system-level SMS permission.
     */
    private fun syncSmsPermissionFromSystem() {
        val hasPermission = hasSmsPermission()
        saveSmsEnabled(hasPermission)
        safeRebuildRows(smsEnabled = hasPermission, locked = false)
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
                PackageManager.PERMISSION_GRANTED

    private fun openAppSettings() {
        val settingsUri = Uri.fromParts("package", packageName, null)
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settingsUri))
    }

    private fun getPhoneNumber(): String? = sharedPreferences.getString(KEY_PHONE_NUMBER, null)

    private fun savePhoneNumber(phoneNumber: String) {
        sharedPreferences.edit { putString(KEY_PHONE_NUMBER, phoneNumber.trim()) }
    }

    private fun isSmsEnabled(): Boolean = sharedPreferences.getBoolean(KEY_SMS_ENABLED, false)

    private fun saveSmsEnabled(enabled: Boolean) {
        sharedPreferences.edit { putBoolean(KEY_SMS_ENABLED, enabled) }
    }

    /**
     * Sign out the current user, or exit guest session.
     */
    private fun handleLogout() {
        val isSignedIn = auth.currentUser != null
        if (isSignedIn) {
            try {
                auth.signOut()
            } catch (exception: Exception) {
                toast("Error during sign out: ${exception.message}")
            }
        }
        launchActivity<LoginActivity>(
            finishCurrent = true,
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        )
    }

    /**
     * Build settings rows with updated values.
     * Call with only the fields you want to change.
     */
    private fun rebuildRows(
        phone: String? = null,
        smsEnabled: Boolean? = null,
        locked: Boolean? = null,
        ownerGroupName: String? = null
    ) {
        val currentSettings = adapter.currentList

        val newPhoneNumber =
            phone ?: currentSettings.filterIsInstance<SettingsRow.PhoneNumber>().firstOrNull()?.value
            ?: getPhoneNumber()

        val newSmsEnabled =
            smsEnabled ?: currentSettings.filterIsInstance<SettingsRow.SmsToggle>().firstOrNull()?.enabled
            ?: isSmsEnabled()

        val isLocked =
            locked ?: currentSettings.filterIsInstance<SettingsRow.SmsToggle>().firstOrNull()?.locked
            ?: false

        val newOwnerGroupName =
            ownerGroupName
                ?: currentSettings.filterIsInstance<SettingsRow.Groups>().firstOrNull()?.ownerGroupName

        adapter.submitList(
            listOf(
                SettingsRow.PhoneNumber(newPhoneNumber),
                SettingsRow.SmsToggle(enabled = newSmsEnabled, locked = isLocked),
                SettingsRow.Account,
                SettingsRow.Groups(newOwnerGroupName)
            )
        )
    }

    /**
     * Guarded rebuild that skips re-binding while the phone field is focused.
     */
    private fun safeRebuildRows(
        phone: String? = null,
        smsEnabled: Boolean? = null,
        locked: Boolean? = null,
        ownerGroupName: String? = null
    ) {
        if (currentFocus?.id == R.id.inputPhoneNumber) return
        rebuildRows(phone, smsEnabled, locked, ownerGroupName)
    }

    /**
     * Open or create the owner's group.
     */
    private fun createOrOpenOwnerGroup() {
        val userId = auth.currentUser?.uid ?: run {
            toast("You must be signed in.")
            return
        }

        val groupReference = firestore.collection("groups").document(userId)
        val defaultName = buildDefaultGroupName()

        groupReference.get()
            .addOnSuccessListener { groupSnapshot ->
                if (groupSnapshot.exists()) {
                    val name = groupSnapshot.getString("name") ?: defaultName
                    upsertOwnerGroupIndex(userId, userId)
                    launchToGroupInventory(userId, name, role = "OWNER")
                } else {
                    val newGroup = mapOf(
                        "ownerUid" to userId,
                        "name" to defaultName,
                        "description" to null,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    groupReference.set(newGroup)
                        .addOnSuccessListener {
                            upsertOwnerGroupIndex(userId, userId)
                            launchToGroupInventory(userId, defaultName, role = "OWNER")
                        }
                        .addOnFailureListener { exception -> showFsError("Create failed", exception) }
                }
            }
            .addOnFailureListener { exception -> showFsError("Read failed", exception) }
    }

    private fun buildDefaultGroupName(): String {
        val user = auth.currentUser
        val name = user?.displayName?.trim()?.takeIf { it.isNotEmpty() }
        val emailPrefix = user?.email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            name != null -> "$name's Group"
            emailPrefix != null -> "$emailPrefix's Group"
            else -> "My Group"
        }
    }

    private fun launchToGroupInventory(groupId: String, groupName: String, role: String) {
        launchActivity<GroupInventoryActivity> {
            putExtra("groupId", groupId)
            putExtra("groupName", groupName)
            putExtra("role", role) // "OWNER"
        }
    }

    private fun upsertOwnerGroupIndex(ownerUid: String, groupId: String) {
        val firestore = FirebaseFirestore.getInstance()
        val ref = firestore.collection("users").document(ownerUid)
            .collection("groups").document(groupId)
        ref.set(
            mapOf(
                "role" to "owner",
                "createdAt" to FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun showFsError(prefix: String, exception: Exception) {
        val errorCode = (exception as? FirebaseFirestoreException)?.code ?: Code.UNKNOWN
        Toast.makeText(this, "$prefix: $errorCode - ${exception.message}", Toast.LENGTH_LONG).show()
    }

    /**
     * Attach listener to update UI when owner's group changes.
     */
    private fun attachOwnerGroupListener() {
        val userId = auth.currentUser?.uid ?: run {
            safeRebuildRows(ownerGroupName = null)
            return
        }

        ownerGroupListener?.remove()
        ownerGroupListener = firestore.collection("groups").document(userId)
            .addSnapshotListener { groupSnapshot, _ ->
                val name = if (groupSnapshot != null && groupSnapshot.exists()) {
                    groupSnapshot.getString("name") ?: buildDefaultGroupName()
                } else {
                    null
                }
                safeRebuildRows(ownerGroupName = name)
            }
    }

    /**
     * Save phone number if phone field loses focus.
     */
    private fun clearFocusAndMaybeSavePhone() {
        val focusedView = currentFocus
        if (focusedView?.id == R.id.inputPhoneNumber) {
            val phoneNumber = (focusedView as? EditText)?.text?.toString().orEmpty()
            savePhoneNumber(phoneNumber)
            focusedView.clearFocus()
            val inputMethodManager: InputMethodManager = systemService()
            inputMethodManager.hideSoftInputFromWindow(focusedView.windowToken, 0)
            safeRebuildRows(phone = phoneNumber)
        }
    }

    /**
     * Confirm deletion of owned group and perform deep delete.
     */
    private fun confirmDeleteOwnedGroup() {
        val userId = auth.currentUser?.uid ?: run {
            toast("Not signed in.")
            return
        }

        val ownerGroupName =
            adapter.currentList.filterIsInstance<SettingsRow.Groups>()
                .firstOrNull()?.ownerGroupName ?: "My Group"

        val dialogView = layoutInflater.inflate(R.layout.confirm_delete_item, null)
        val confirmationMessageTextView = dialogView.findViewById<android.widget.TextView>(R.id.textDeleteConfirmMessage)
        confirmationMessageTextView.text = getString(R.string.delete_group_confirmation, ownerGroupName)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val confirmButton = dialogView.findViewById<android.widget.Button>(R.id.buttonDeleteConfirm)
        val cancelButton  = dialogView.findViewById<android.widget.Button>(R.id.buttonDeleteCancel)

        confirmButton.setOnClickListener {
            lifecycleScope.launch {
                confirmButton.isEnabled = false
                cancelButton.isEnabled = false

                val didDelete = deleteGroupDeepAsync(userId)

                if (didDelete) {
                    safeRebuildRows(ownerGroupName = null)
                } else {
                    toast("Delete failed. Check your connection and permissions.")
                }
                dialog.dismiss()
            }
        }
        cancelButton.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    companion object {
        private const val PREFS_NAME = "settings_prefs"
        private const val KEY_PHONE_NUMBER = "phone_number"
        private const val KEY_SMS_ENABLED = "sms_enabled"
    }
}

/**
 * RecyclerView decoration for adding vertical spacing between rows.
 */
class VerticalSpaceDecoration(private val verticalSpaceHeight: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position >= 0) outRect.top = verticalSpaceHeight
    }
}
