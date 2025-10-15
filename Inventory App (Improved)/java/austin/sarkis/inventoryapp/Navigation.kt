package austin.sarkis.inventoryapp

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

/**
 * Navigation and system helpers.
 *
 * These small, inline, reified utilities reduce boilerplate for launching Activities
 * and retrieving typed system services.
 */

/**
 * Create an [Intent] for Activity type [T], allowing the caller to apply extras/flags
 * via [block]. Example:
 *
 * ```kotlin
 * val intent = context.intentFor<DetailActivity> {
 *     putExtra(EXTRA_ID, id)
 *     addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
 * }
 * startActivity(intent)
 * ```
 */
inline fun <reified T : Any> Context.intentFor(block: Intent.() -> Unit = {}): Intent =
    Intent(this, T::class.java).apply(block)

/**
 * Launch an Activity of type [T].
 *
 * @param finishCurrent if true, calls [AppCompatActivity.finish] immediately after `startActivity`.
 * @param flags optional bitwise OR of [Intent] flags to set on the new Intent.
 * @param extras lambda to add extras to the created Intent (keys/values, categories, etc.).
 */
inline fun <reified T : AppCompatActivity> AppCompatActivity.launchActivity(
    finishCurrent: Boolean = false,
    flags: Int? = null,
    extras: Intent.() -> Unit = {}
) {
    val intent = intentFor<T> {
        flags?.let { this.flags = it }
        extras()
    }
    startActivity(intent)
    if (finishCurrent) finish()
}

/**
 * Retrieve a typed Android system service.
 *
 * Example:
 * ```kotlin
 * val smsManager: SmsManager = context.systemService()
 * ```
 */
inline fun <reified T> Context.systemService(): T =
    getSystemService(T::class.java) as T
