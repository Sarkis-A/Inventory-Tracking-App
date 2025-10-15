package austin.sarkis.inventoryapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

/**
 * LoginActivity handles user authentication and account creation.
 * Users can log in with existing credentials or create a new account,
 * which is stored in the local SQLite database.
 */
public class LoginActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_page);

        // Initialize the database helper
        dbHelper = new DatabaseHelper(this);

        // Bind input fields
        EditText usernameEditText = findViewById(R.id.editTextText3);
        EditText passwordEditText = findViewById(R.id.editTextTextPassword);

        // Navigate to settings activity when gear icon is clicked
        ImageButton settingsButton = findViewById(R.id.imageButton);
        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        // Handle log in button click
        Button loginButton = findViewById(R.id.button);
        loginButton.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            // Check if credentials are valid
            if (dbHelper.checkUser(username, password)) {
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                startActivity(intent);
                finish(); // End login activity so it's not in the back stack
            } else {
                Toast.makeText(LoginActivity.this, "Invalid username or password", Toast.LENGTH_SHORT).show();
            }
        });

        // Handle account creation
        Button createAccountButton = findViewById(R.id.button2);
        createAccountButton.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Please enter both username and password", Toast.LENGTH_SHORT).show();
                return;
            }

            // Prevent duplicate usernames
            if (dbHelper.checkUsernameExists(username)) {
                Toast.makeText(LoginActivity.this, "Username already exists. Please choose another.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Create account and notify user
            long id = dbHelper.insertUser(username, password);
            if (id != -1) {
                Toast.makeText(LoginActivity.this, "Account created! Please log in.", Toast.LENGTH_SHORT).show();
                usernameEditText.setText("");
                passwordEditText.setText("");
            } else {
                Toast.makeText(LoginActivity.this, "Account creation failed.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
