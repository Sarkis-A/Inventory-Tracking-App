package austin.sarkis.inventoryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * AddEditItemActivity allows users to either add a new inventory item
 * or edit an existing one. It detects mode via Intent extras and provides
 * a simple form UI to update the database.
 */
public class AddEditItemActivity extends AppCompatActivity {

    private EditText nameEditText;
    private EditText descriptionEditText;
    private DatabaseHelper dbHelper;
    private long editingItemId = -1; // -1 means add mode

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_edit_item);

        // Initialize database helper
        dbHelper = new DatabaseHelper(this);

        // Bind input fields
        nameEditText = findViewById(R.id.editTextProductName);
        descriptionEditText = findViewById(R.id.editTextDescription);
        EditText quantityEditText = findViewById(R.id.editTextQuantity);

        // Bind buttons
        Button addButton = findViewById(R.id.buttonAdd);
        Button saveButton = findViewById(R.id.buttonSave);
        Button incrementButton = findViewById(R.id.buttonIncrement);
        Button decrementButton = findViewById(R.id.buttonDecrement);
        ImageButton backButton = findViewById(R.id.buttonBack);

        // Go back to MainActivity on back arrow
        backButton.setOnClickListener(v -> {
            Intent intent = new Intent(AddEditItemActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        // Check if we're editing an existing item
        Intent intent = getIntent();
        if (intent.hasExtra("item_id")) {
            // Edit mode: get item data from intent and fill fields
            editingItemId = intent.getLongExtra("item_id", -1);
            setButtonEnabled(addButton, false);
            setButtonEnabled(saveButton, true);
            nameEditText.setText(intent.getStringExtra("product_name"));
            descriptionEditText.setText(intent.getStringExtra("description"));
            quantityEditText.setText(String.valueOf(intent.getIntExtra("quantity", 0)));
        } else {
            // Add mode: disable save, enable add
            setButtonEnabled(addButton, true);
            setButtonEnabled(saveButton, false);
        }

        // Handle "Add" button
        addButton.setOnClickListener(v -> {
            String name = nameEditText.getText().toString().trim();
            String description = descriptionEditText.getText().toString().trim();
            int quantity;
            try {
                quantity = Integer.parseInt(quantityEditText.getText().toString().trim());
            } catch (NumberFormatException e) {
                quantity = 0;
            }

            long id = dbHelper.insertInventoryItem(name, description, quantity);
            if (id != -1) {
                Toast.makeText(this, "Item added!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "Failed to add item.", Toast.LENGTH_SHORT).show();
            }
        });

        // Handle "Save" button
        saveButton.setOnClickListener(v -> {
            if (editingItemId == -1) {
                Toast.makeText(this, "No item selected for editing.", Toast.LENGTH_SHORT).show();
                return;
            }
            String name = nameEditText.getText().toString().trim();
            String description = descriptionEditText.getText().toString().trim();
            int quantity;
            try {
                quantity = Integer.parseInt(quantityEditText.getText().toString().trim());
            } catch (NumberFormatException e) {
                quantity = 0;
            }

            int rows = dbHelper.updateInventoryItem(editingItemId, name, description, quantity);
            if (rows > 0) {
                Toast.makeText(this, "Item updated!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "Failed to update item.", Toast.LENGTH_SHORT).show();
            }
        });

        // Increase quantity
        incrementButton.setOnClickListener(v -> {
            int quantity;
            try {
                quantity = Integer.parseInt(quantityEditText.getText().toString());
            } catch (NumberFormatException e) {
                quantity = 0;
            }
            quantity += 1;
            quantityEditText.setText(String.valueOf(quantity));
        });

        // Decrease quantity, preventing negative numbers
        decrementButton.setOnClickListener(v -> {
            int quantity;
            try {
                quantity = Integer.parseInt(quantityEditText.getText().toString());
            } catch (NumberFormatException e) {
                quantity = 0;
            }
            if (quantity > 0) {
                quantity -= 1;
                quantityEditText.setText(String.valueOf(quantity));
            }
        });
    }

    // Helper method to enable or disable a button with opacity change
    private void setButtonEnabled(Button button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1.0f : 0.5f);
    }
}
