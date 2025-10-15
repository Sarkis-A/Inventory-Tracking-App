package austin.sarkis.inventoryapp

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.SignInButton
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseUser


/**
 * Login screen providing three authentication flows:
 * 1. **Email/Password** login and sign-up with Firebase Auth.
 * 2. **Google Sign-In** via Android Credential Manager.
 * 3. **Guest Mode** with no Firebase account (local-only).
 *
 * Behavior:
 * - After any successful sign-in, [createOrUpdateUserProfile] merges the user profile into Firestore.
 * - Guest mode sets a shared preference flag (`is_guest`) to drive offline usage.
 * - If already signed in, [onStart] navigates directly to [MainActivity].
 */
class LoginActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val credentialManager by lazy { CredentialManager.create(this) }

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.login_page)

        // Bind inputs
        emailInput = findViewById(R.id.inputEmail)
        passwordInput  = findViewById(R.id.inputPassword)

        // --- Email / Password: Log in ---
        findViewById<Button>(R.id.buttonLogin).setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password  = passwordInput.text.toString().trim()
            if (!validateCredentials(email, password)) return@setOnClickListener

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    createOrUpdateUserProfile()
                    navigateToMain()
                }
                .addOnFailureListener { exception -> toast("Login failed: ${exception.localizedMessage}") }
        }

        // --- Email / Password: Sign up ---
        findViewById<Button>(R.id.buttonSignUp).setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password  = passwordInput.text.toString().trim()
            if (!validateCredentials(email, password)) return@setOnClickListener

            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener {
                    createOrUpdateUserProfile()
                    navigateToMain()
                }
                .addOnFailureListener { exception -> toast("Sign-up failed: ${exception.localizedMessage}") }
        }

        // --- Guest mode ---
        findViewById<Button?>(R.id.buttonGuest)?.setOnClickListener {
            getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit { putBoolean("is_guest", true) }
            toast("Continuing as Guest")
            navigateToMain()
        }

        // --- Google Sign-In ---
        findViewById<SignInButton>(R.id.buttonGoogleSignIn).apply {
            setOnClickListener { signInWithGoogleCredentialManager() }
        }
    }

    override fun onStart() {
        super.onStart()
        auth.currentUser?.let { user ->
            // best-effort backfill to ensure index exists
            upsertUserEmailIndex(user)
            navigateToMain()
        }
    }

    /**
     * Handles Google Sign-In using Android Credential Manager.
     * Exchanges a Google ID token for a Firebase credential.
     */
    private fun signInWithGoogleCredentialManager() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@LoginActivity, request)
                val cred = result.credential
                if (cred is CustomCredential &&
                    cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleTokenCred = GoogleIdTokenCredential.createFrom(cred.data)
                    val idToken = googleTokenCred.idToken
                    if (idToken.isEmpty()) {
                        toast("Google token missing")
                        return@launch
                    }
                    val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
                    auth.signInWithCredential(firebaseCred)
                        .addOnSuccessListener {
                            createOrUpdateUserProfile()
                            navigateToMain()
                        }
                        .addOnFailureListener { exception ->
                            toast("Google sign-in failed: ${exception.localizedMessage}")
                        }
                } else {
                    toast("No Google credential returned.")
                }
            } catch (_: NoCredentialException) {
                toast("No Google accounts available on this device.")
            } catch (_: GetCredentialCancellationException) {
                toast("Sign-in canceled.")
            } catch (exception: GetCredentialException) {
                toast("Credential error: ${exception.message}")
            } catch (throwable: Throwable) {
                toast("Unexpected error: ${throwable.localizedMessage}")
            }
        }
    }

    /**
     * Create or merge the current user's Firestore profile document.
     * Fields: displayName, email, createdAt, lastLoginAt.
     */
    private fun createOrUpdateUserProfile() {
        val user = auth.currentUser ?: return
        val firestore = FirebaseFirestore.getInstance()
        val userProfileData = mapOf(
            "displayName" to (user.displayName ?: ""),
            "email" to (user.email ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
            "lastLoginAt" to FieldValue.serverTimestamp()
        )
        firestore.collection("users").document(user.uid)
            .set(userProfileData, SetOptions.merge())
            .addOnSuccessListener {
                // After profile upsert succeeds, upsert email→userId index (idempotent).
                upsertUserEmailIndex(user)
            }
            .addOnFailureListener {
                // Even if profile write fails, attempt the index upsert (best-effort).
                upsertUserEmailIndex(user)
            }
    }

    /** Validate basic email and password requirements. */
    private fun validateCredentials(email: String, password: String): Boolean {
        if (email.isEmpty() || password.isEmpty()) {
            toast("Enter email and password")
            return false
        }
        if (password.length < 6) {
            toast("Password must be at least 6 characters")
            return false
        }
        return true
    }

    /** Upserts the public email→userId mapping used by the "add member by email" flow.
     *
     *  SECURITY NOTE: This exposes UIDs to any signed-in client who knows the email.
     *  Replace with a server-side lookup when possible.
     */
    private fun upsertUserEmailIndex(user: FirebaseUser) {
        val uid = user.uid
        val lowercasedEmail = user.email?.trim()?.lowercase() ?: return  // nothing to index without an email

        FirebaseFirestore.getInstance()
            .collection("userEmails")
            .document(lowercasedEmail)
            .set(mapOf("userId" to uid, "email" to lowercasedEmail), SetOptions.merge())
            .addOnFailureListener {
                // Non-fatal. We don't block login on index failure.
            }
    }

    /** Navigate to the main activity and finish the login screen. */
    private fun navigateToMain() = launchActivity<MainActivity>(finishCurrent = true)

    /** Show a short toast message. */
    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}