package austin.sarkis.inventoryapp;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * InventoryAdapter connects the inventory data with the RecyclerView UI.
 * It handles displaying item info, and launching edit/delete actions for each item.
 */
public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.InventoryViewHolder> {

    private List<InventoryItem> itemList;
    private DatabaseHelper dbHelper;
    private ActivityResultLauncher<Intent> editItemLauncher;

    public InventoryAdapter(List<InventoryItem> itemList, DatabaseHelper dbHelper, ActivityResultLauncher<Intent> editItemLauncher) {
        this.itemList = itemList;
        this.dbHelper = dbHelper;
        this.editItemLauncher = editItemLauncher;
    }

    @NonNull
    @Override
    public InventoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the item layout
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.inventory_item, parent, false);
        return new InventoryViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull InventoryViewHolder holder, int position) {
        InventoryItem item = itemList.get(position);

        // Set product name
        holder.productName.setText(item.getProductName());

        // Format quantity text using localized string
        String quantityText = holder.itemView.getContext()
                .getString(R.string.quantity, String.valueOf(item.getQuantityInt()));
        holder.quantity.setText(quantityText);

        // Set image
        holder.productImage.setImageResource(item.getImageResId());

        // Edit button action
        holder.buttonEdit.setOnClickListener(v -> {
            Context context = holder.itemView.getContext();
            Intent intent = new Intent(context, AddEditItemActivity.class);
            intent.putExtra("item_id", item.getId());
            intent.putExtra("product_name", item.getProductName());
            intent.putExtra("description", item.getDescription());
            intent.putExtra("quantity", item.getQuantityInt());
            editItemLauncher.launch(intent);
        });

        // Delete button with confirmation dialog
        holder.buttonDelete.setOnClickListener(v -> {
            Context context = holder.itemView.getContext();

            // Inflate confirmation dialog view
            LayoutInflater inflater = LayoutInflater.from(context);
            View dialogView = inflater.inflate(R.layout.confirm_delete_item, null);

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                    .setView(dialogView)
                    .setCancelable(true);

            AlertDialog dialog = builder.create();

            Button confirmButton = dialogView.findViewById(R.id.buttonConfirmDelete);
            Button cancelButton = dialogView.findViewById(R.id.buttonCancelDelete);

            // Confirm deletion
            confirmButton.setOnClickListener(dv -> {
                long itemId = item.getId();
                int rows = dbHelper.deleteInventoryItem(itemId);
                if (rows > 0) {
                    removeItem(holder.getAdapterPosition());
                    Toast.makeText(context, "Item deleted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "Failed to delete item", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            });

            // Cancel dialog
            cancelButton.setOnClickListener(dv -> dialog.dismiss());

            dialog.show();
        });
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    // Remove item from the list and notify the adapter
    public void removeItem(int position) {
        itemList.remove(position);
        notifyItemRemoved(position);
    }

    // ViewHolder class that holds item views
    public static class InventoryViewHolder extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView productName, quantity;
        Button buttonEdit, buttonDelete;

        public InventoryViewHolder(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.imageViewProduct);
            productName = itemView.findViewById(R.id.textViewProductName);
            quantity = itemView.findViewById(R.id.textViewQuantity);
            buttonEdit = itemView.findViewById(R.id.buttonEdit);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
        }
    }
}
