package austin.sarkis.inventoryapp

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Adapter that displays local inventory items in a RecyclerView.
 *
 * It binds [InventoryItem] data to the `inventory_item` layout, launches the
 * edit screen when requested, and handles item deletion with a confirmation
 * dialog and database update.
 */
class LocalItemAdapter(
    private val items: MutableList<InventoryItem>,
    private val dbHelper: LocalInventoryDatabase,
    private val editItemLauncher: ActivityResultLauncher<Intent>?
) : RecyclerView.Adapter<LocalItemAdapter.ItemViewHolder>() {

    /**
     * Inflates an `inventory_item` row and wraps it in a [ItemViewHolder].
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.inventory_item, parent, false)
        return ItemViewHolder(itemView)
    }

    /**
     * Binds the item at [position] to UI and wires click listeners.
     */
    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]

        holder.nameText.text = item.name ?: ""
        val quantityText = holder.itemView.context.getString(R.string.quantity, item.quantity)
        holder.quantityText.text = quantityText

        holder.thumbnailImage.setImageResource(item.imageResId)

        // --- Edit flow ---
        holder.editButton.setOnClickListener {
            val context = holder.itemView.context
            val intent = context.intentFor<AddEditItemActivity> {
                putExtra(AddEditItemActivity.EXTRA_MODE, AddEditItemActivity.MODE_GUEST)
                putExtra("localId", item.id)
                putExtra(AddEditItemActivity.EXTRA_NAME, item.name)
                putExtra(AddEditItemActivity.EXTRA_DESC, item.description)
                putExtra(AddEditItemActivity.EXTRA_QUANTITY, item.quantity)
            }
            editItemLauncher?.launch(intent)
        }

        // --- Delete flow ---
        holder.deleteButton.setOnClickListener {
            val context = holder.itemView.context

            val dialogView = LayoutInflater.from(context).inflate(R.layout.confirm_delete_item, null)

            val dialog = MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setCancelable(true)
                .create()

            val confirmButton = dialogView.findViewById<Button>(R.id.buttonDeleteConfirm)
            val cancelButton = dialogView.findViewById<Button>(R.id.buttonDeleteCancel)

            confirmButton.setOnClickListener {
                val rows = dbHelper.deleteItem(item.id)
                if (rows > 0) {
                    val adapterPos = holder.bindingAdapterPosition
                    if (adapterPos != RecyclerView.NO_POSITION) {
                        removeItem(adapterPos)
                    }
                    Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to delete item", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            cancelButton.setOnClickListener { dialog.dismiss() }
            dialog.show()
        }
    }

    /** Returns the number of items currently in the adapter. */
    override fun getItemCount(): Int = items.size

    /**
     * Removes the item at [position] from [items] and notifies RecyclerView.
     */
    fun removeItem(position: Int) {
        if (position in 0 until items.size) {
            items.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    /**
     * Holds references to views within an `inventory_item` row.
     */
    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnailImage: ImageView = itemView.findViewById(R.id.imageItemThumbnail)
        val nameText: TextView = itemView.findViewById(R.id.textItemName)
        val quantityText: TextView = itemView.findViewById(R.id.textItemQuantity)
        val editButton: Button = itemView.findViewById(R.id.buttonEditItem)
        val deleteButton: Button = itemView.findViewById(R.id.buttonDeleteItem)
    }
}
