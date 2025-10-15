package austin.sarkis.inventoryapp

/**
 * Represents a row in the Settings screen RecyclerView.
 * Each subclass corresponds to a different type of setting row.
 */
sealed class SettingsRow {

    /**
     * Row for entering and displaying the user's phone number.
     *
     * @param value the current phone number, or null if not set.
     */
    data class PhoneNumber(val value: String?) : SettingsRow()

    /**
     * Row for enabling or disabling SMS notifications.
     *
     * @param enabled true if SMS notifications are currently enabled.
     * @param locked true if the toggle is locked (e.g., while permission dialog is in progress).
     */
    data class SmsToggle(val enabled: Boolean, val locked: Boolean = false) : SettingsRow()

    /**
     * Row for account management actions such as logout.
     */
    data object Account : SettingsRow()

    /**
     * Row for displaying and managing group ownership and membership.
     *
     * @param ownerGroupName the name of the group owned by the user,
     * or null if the user does not own a group.
     */
    data class Groups(val ownerGroupName: String?) : SettingsRow()
}
