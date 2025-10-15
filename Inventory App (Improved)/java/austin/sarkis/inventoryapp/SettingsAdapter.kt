package austin.sarkis.inventoryapp

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for rendering the Settings screen.
 *
 * Supports multiple row types defined in [SettingsRow]:
 * - Phone number entry
 * - SMS notification toggle
 * - Account actions (logout)
 * - Group-related actions (open/create, view, delete)
 */
class SettingsAdapter(
    private val onSavePhone: (String) -> Unit,
    private val onSmsToggled: (Boolean) -> Unit,
    private val onLogout: () -> Unit,
    private val onOpenOrCreateMyGroup: () -> Unit,
    private val onViewMyGroups: () -> Unit,
    private val onDeleteMyGroup: () -> Unit
) : ListAdapter<SettingsRow, RecyclerView.ViewHolder>(SettingsDiffCallback()) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is SettingsRow.PhoneNumber -> VIEW_PHONE
        is SettingsRow.SmsToggle   -> VIEW_SMS
        is SettingsRow.Account     -> VIEW_ACCOUNT
        is SettingsRow.Groups      -> VIEW_GROUPS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_PHONE   -> PhoneVH(inflater.inflate(R.layout.item_setting_phone, parent, false), onSavePhone)
            VIEW_SMS     -> SmsVH(inflater.inflate(R.layout.item_setting_sms, parent, false), onSmsToggled)
            VIEW_ACCOUNT -> AccountVH(inflater.inflate(R.layout.item_setting_account, parent, false), onLogout)
            else         -> GroupsVH(inflater.inflate(R.layout.item_setting_groups, parent, false), onOpenOrCreateMyGroup, onViewMyGroups, onDeleteMyGroup)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is SettingsRow.PhoneNumber -> (holder as PhoneVH).bind(row)
            is SettingsRow.SmsToggle   -> (holder as SmsVH).bind(row)
            is SettingsRow.Account     -> (holder as AccountVH).bind()
            is SettingsRow.Groups      -> (holder as GroupsVH).bind(row)
        }
    }

    // ---------- ViewHolders ----------

    /**
     * ViewHolder for phone number input.
     * Saves phone number on IME "Done" or when focus is lost.
     */
    private class PhoneVH(
        view: View,
        private val onSavePhone: (String) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val input = view.findViewById<EditText>(R.id.inputPhoneNumber)

        fun bind(row: SettingsRow.PhoneNumber) {
            // Set only if text changed, to avoid cursor jump
            val newText = row.value.orEmpty()
            if (input.text.toString() != newText) {
                input.setText(newText)
                input.setSelection(input.text.length)
            }

            // Save when "Done" pressed and close keyboard
            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    val number = input.text.toString()
                    onSavePhone(number)
                    val imm = itemView.context.getSystemService<InputMethodManager>()
                    imm?.hideSoftInputFromWindow(input.windowToken, 0)
                    input.clearFocus()
                    true
                } else {
                    false
                }
            }

            // Save when focus is lost
            input.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    onSavePhone(input.text.toString())
                }
            }
        }
    }

    /**
     * ViewHolder for SMS notifications toggle.
     * Locks the toggle if disabled and updates state on change.
     */
    private class SmsVH(
        view: View,
        private val onSmsToggled: (Boolean) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val toggle = view.findViewById<SwitchCompat>(R.id.switchSmsNotifications)

        fun bind(row: SettingsRow.SmsToggle) {
            toggle.setOnCheckedChangeListener(null) // Prevent triggering during binding
            toggle.isEnabled = !row.locked
            toggle.isChecked = row.enabled
            toggle.setOnCheckedChangeListener { _, checked -> onSmsToggled(checked) }
        }
    }

    /**
     * ViewHolder for account section.
     * Provides logout functionality.
     */
    private class AccountVH(
        view: View,
        private val onLogout: () -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val logout = view.findViewById<Button>(R.id.buttonLogout)
        fun bind() {
            logout.setOnClickListener { onLogout() }
        }
    }

    /**
     * ViewHolder for group actions.
     * Allows open/create, view, and delete (owner only).
     */
    private class GroupsVH(
        view: View,
        private val onOpenOrCreateMyGroup: () -> Unit,
        private val onViewMyGroups: () -> Unit,
        private val onDeleteMyGroup: () -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val status = view.findViewById<TextView>(R.id.textOwnerGroupStatus)
        private val openOrCreate = view.findViewById<Button>(R.id.buttonOpenOrCreateMyGroup)
        private val viewList = view.findViewById<Button>(R.id.buttonViewMyGroups)
        private val deleteBtn = view.findViewById<Button>(R.id.buttonDeleteMyGroup)

        fun bind(row: SettingsRow.Groups) {
            status.text = if (row.ownerGroupName.isNullOrBlank()) {
                status.context.getString(R.string.group_status_none)
            } else {
                status.context.getString(R.string.group_status_owned, row.ownerGroupName)
            }

            openOrCreate.setOnClickListener { onOpenOrCreateMyGroup() }
            viewList.setOnClickListener { onViewMyGroups() }

            // Delete button visible only if user owns a group
            if (!row.ownerGroupName.isNullOrBlank()) {
                deleteBtn.visibility = View.VISIBLE
                deleteBtn.setOnClickListener { onDeleteMyGroup() }
            } else {
                deleteBtn.visibility = View.GONE
                deleteBtn.setOnClickListener(null)
            }
        }
    }

    private companion object {
        const val VIEW_PHONE = 0
        const val VIEW_SMS = 1
        const val VIEW_ACCOUNT = 2
        const val VIEW_GROUPS = 3
    }
}

/**
 * DiffUtil callback for Settings rows.
 * Compares row types as identity; compares contents for changes.
 */
class SettingsDiffCallback : DiffUtil.ItemCallback<SettingsRow>() {
    override fun areItemsTheSame(oldItem: SettingsRow, newItem: SettingsRow): Boolean {
        return when {
            oldItem is SettingsRow.PhoneNumber && newItem is SettingsRow.PhoneNumber -> true
            oldItem is SettingsRow.SmsToggle   && newItem is SettingsRow.SmsToggle   -> true
            oldItem is SettingsRow.Account     && newItem is SettingsRow.Account     -> true
            oldItem is SettingsRow.Groups      && newItem is SettingsRow.Groups      -> true
            else -> false
        }
    }
    override fun areContentsTheSame(oldItem: SettingsRow, newItem: SettingsRow): Boolean = oldItem == newItem
}
