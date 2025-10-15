package austin.sarkis.inventoryapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for cloud-backed inventory items.
 *
 * Uses [ListAdapter] and [DiffUtil.ItemCallback] for efficient updates with [submitList].
 * Only changed rows re-bind, making it scalable for large datasets.
 *
 * Row layout: R.layout.inventory_item, containing:
 *  - TextView  @id/textItemName
 *  - TextView  @id/textItemQuantity
 *  - Button    @id/buttonEditItem
 *  - Button    @id/buttonDeleteItem
 */
class CloudItemAdapter(
    private val onEditItem: (CloudItem) -> Unit,
    private val onDeleteItem: (CloudItem) -> Unit
) : ListAdapter<CloudItem, CloudItemAdapter.VH>(Diff()) {

    init {
        // Enable stable IDs so RecyclerView can efficiently reuse ViewHolders.
        setHasStableIds(true)
    }

    /**
     * Provides a stable row ID derived from the item's Firestore document ID.
     */
    override fun getItemId(position: Int): Long {
        return getItem(position).id.hashCode().toLong()
    }

    /**
     * Creates a new ViewHolder by inflating the inventory row layout.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.inventory_item, parent, false)
        return VH(view, onEditItem, onDeleteItem)
    }

    /**
     * Binds the item at [position] to the given ViewHolder.
     */
    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    /**
     * ViewHolder for a single inventory row.
     * Handles UI binding and wiring up edit/delete callbacks.
     */
    class VH(
        itemView: View,
        private val onEditItem: (CloudItem) -> Unit,
        private val onDeleteItem: (CloudItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val name: TextView = itemView.findViewById(R.id.textItemName)
        private val quantityTextView: TextView = itemView.findViewById(R.id.textItemQuantity)
        private val editItemButton: Button = itemView.findViewById(R.id.buttonEditItem)
        private val deleteItemButton: Button = itemView.findViewById(R.id.buttonDeleteItem)

        /**
         * Populates the row with item data and sets up click handlers.
         */
        fun bind(item: CloudItem) {
            name.text = item.name
            quantityTextView.text = itemView.context.getString(R.string.quantity, item.quantity)

            // Prevent old listeners from being reused on recycled rows.
            editItemButton.setOnClickListener(null)
            deleteItemButton.setOnClickListener(null)

            editItemButton.setOnClickListener { onEditItem(item) }
            deleteItemButton.setOnClickListener { onDeleteItem(item) }
        }
    }

    /**
     * DiffUtil callback to detect changes in row identity and content.
     */
    class Diff : DiffUtil.ItemCallback<CloudItem>() {
        override fun areItemsTheSame(oldItem: CloudItem, newItem: CloudItem): Boolean {
            // Rows are the same if they represent the same Firestore document.
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CloudItem, newItem: CloudItem): Boolean {
            // Rows are unchanged if all displayed fields are equal.
            return oldItem == newItem
        }
    }
}
