package austin.sarkis.inventoryapp

import androidx.annotation.DrawableRes

/**
 * Data model for a locally stored inventory item.
 *
 * Represents a single row in the local SQLite database.
 *
 * @property id unique row identifier (primary key)
 * @property name item name (required)
 * @property description optional item description
 * @property quantity current stock quantity
 * @property imageResId optional drawable resource ID for displaying a thumbnail/icon
 */
data class InventoryItem(
    val id: Long,
    var name: String?,
    var description: String?,
    var quantity: Int,
    @param:DrawableRes var imageResId: Int = R.drawable.ic_launcher_foreground  // default placeholder if none provided
)
