package austin.sarkis.inventoryapp;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * MainActivity is the home screen of the inventory app.
 * It displays a list of inventory items, allows users to add/edit items,
 * and checks for low inventory to send SMS notifications (if permitted).
 * It also prompts the user once to grant SMS permission via a custom dialog.
 */
public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DatabaseHelper dbHelper;
    private ActivityResultLauncher<Intent> addEditItemLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.recyclerViewInventory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Get reference to the Add (+) button
        View plusButton = findViewById(R.id.buttonAdd);

        // Initialize database helper
        dbHelper = new DatabaseHelper(this);

        // Set up result launcher for Add/Edit activity
        addEditItemLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        reloadInventoryList();
                        notifyLowInventoryItems(); // Trigger low inventory SMS alert
                    }
                }
        );

        // Launch Add/Edit activity when user taps the add button
        plusButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddEditItemActivity.class);
            addEditItemLauncher.launch(intent);
        });

        // Load inventory list when activity starts
        reloadInventoryList();

        // Show SMS permission dialog only once
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean smsDialogShown = prefs.getBoolean("sms_dialog_shown", false);
        if (!smsDialogShown) {
            showSmsPermissionDialog();
            prefs.edit().putBoolean("sms_dialog_shown", true).apply();
        }
    }

    // Loads inventory from the database and displays in the RecyclerView
    private void reloadInventoryList() {
        if (dbHelper == null) {
            dbHelper = new DatabaseHelper(this);
        }
        List<InventoryItem> itemList = dbHelper.getAllInventoryItemsList();
        InventoryAdapter adapter = new InventoryAdapter(itemList, dbHelper, addEditItemLauncher);
        recyclerView.setAdapter(adapter);
    }

    // Displays a custom dialog prompting for SMS permission
    private void showSmsPermissionDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.sms_permission_activity, null);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setCancelable(true);

        AlertDialog dialog = builder.create();

        Button grantButton = dialogView.findViewById(R.id.buttonGrantSms);
        Button denyButton = dialogView.findViewById(R.id.buttonDenySms);

        // Launch settings screen when user grants SMS permission
        grantButton.setOnClickListener(v -> {
            dialog.dismiss();
            Intent settingsIntent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(settingsIntent);
        });

        // Dismiss dialog and show message if user denies
        denyButton.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "SMS permission denied.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    // Checks if SMS permission is granted
    private boolean hasSmsSendPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    // Sends a low-stock SMS to the configured phone number
    private void sendLowStockSms(String message) {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String phoneNumber = prefs.getString("sms_phone_number", "");

        if (phoneNumber.trim().isEmpty()) {
            Toast.makeText(this, "No phone number set for SMS notifications.", Toast.LENGTH_SHORT).show();
            return;
        }

        SmsManager smsManager = getSystemService(SmsManager.class);
        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
    }

    // Sends notifications for items with low stock
    private void notifyLowInventoryItems() {
        if (!hasSmsSendPermission()) {
            Toast.makeText(this, "SMS permission not granted. Cannot send notifications.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<InventoryItem> itemList = dbHelper.getAllInventoryItemsList();
        for (InventoryItem item : itemList) {
            long id = item.getId();
            int quantity = item.getQuantityInt();

            if (quantity <= 5) {
                if (!hasAlreadyNotified(id)) {
                    sendLowStockSms("Your inventory is low for: " + item.getProductName());
                    setNotifiedFlag(id, true);
                }
            } else {
                setNotifiedFlag(id, false); // Reset flag if quantity is now okay
            }
        }
    }

    // Checks if this item has already triggered a low-stock SMS
    private boolean hasAlreadyNotified(long itemId) {
        return getSharedPreferences("sms_notified_items", MODE_PRIVATE)
                .getBoolean("notified_" + itemId, false);
    }

    // Sets or clears the "notified" flag for an item
    private void setNotifiedFlag(long itemId, boolean notified) {
        getSharedPreferences("sms_notified_items", MODE_PRIVATE)
                .edit()
                .putBoolean("notified_" + itemId, notified)
                .apply();
    }
}
