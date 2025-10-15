package austin.sarkis.inventoryapp

/**
 * Cloud-backed representation of an inventory item.
 *
 * Instances of this data class mirror Firestore documents under
 * `users/{userId}/items/{documentId}` and are used by UI layers/adapters
 * when the app is operating in **cloud** mode.
 *
 * @property id Firestore document ID for this item (not a human-readable SKU).
 * @property name Display name of the item shown in lists and edit screens.
 * @property description Optional detail text; `null` or empty when not provided.
 * @property quantity Current counted quantity. Expected to be non-negative.
 */
data class CloudItem(
    val id: String,
    val name: String,
    val description: String?,
    val quantity: Int,

)
