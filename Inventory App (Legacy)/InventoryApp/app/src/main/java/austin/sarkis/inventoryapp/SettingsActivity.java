package austin.sarkis.inventoryapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;

public class SettingsActivity extends AppCompatActivity {

    private EditText phoneNumberEditText;
    private SwitchCompat smsSwitch;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        // Back button
        ImageButton backButton = findViewById(R.id.buttonBack);
        backButton.setOnClickListener(v -> finish());

        // SharedPreferences setup
        prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);

        // Phone number EditText
        phoneNumberEditText = findViewById(R.id.editTextPhoneNumber);

        // Display saved phone number (initial load)
        String savedNumber = prefs.getString("sms_phone_number", "");
        phoneNumberEditText.setText(savedNumber);

        // Save number when focus leaves the field
        phoneNumberEditText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String number = phoneNumberEditText.getText().toString();
                prefs.edit().putString("sms_phone_number", number).apply();
            }
        });

        // SMS Switch
        smsSwitch = findViewById(R.id.switchSmsNotifications);
        smsSwitch.setChecked(hasSmsPermission());

        smsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!hasSmsPermission()) {
                    ActivityCompat.requestPermissions(SettingsActivity.this,
                            new String[]{Manifest.permission.SEND_SMS}, 2002);
                }
            } else {
                // Guide user to revoke permission in system settings
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
                // Optionally, reset the switch state until user actually disables it
                smsSwitch.setChecked(hasSmsPermission());
            }
        });
    }

    // Always reload latest saved phone number when returning to settings
    @Override
    protected void onResume() {
        super.onResume();
        prefs.getString("sms_phone_number", "");
        smsSwitch.setChecked(hasSmsPermission());
    }

    @Override
    protected void onPause() {
        super.onPause();
        String number = phoneNumberEditText.getText().toString();
        prefs.edit().putString("sms_phone_number", number).apply();
    }

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2002) {
            smsSwitch.setChecked(hasSmsPermission());
            if (hasSmsPermission()) {
                Toast.makeText(this, "SMS permission granted.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "SMS permission denied.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
