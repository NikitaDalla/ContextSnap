package com.example.contextsnap

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.annotation.RequiresApi

// Optional fingerprint quick-login (uses Android's built-in BiometricPrompt, API 28+).
// The fingerprint itself never reaches the app: Android only reports "matched" or "failed".
// We remember WHICH user turned fingerprint login on; the password login always still works.
object Fingerprint {
    private fun prefs(c: Context) = c.getSharedPreferences("bio", Context.MODE_PRIVATE)

    fun isAvailable(c: Context): Boolean =
        Build.VERSION.SDK_INT >= 28 && c.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)

    fun enabledUser(c: Context): Long = prefs(c).getLong("user_id", -1L)
    fun enableFor(c: Context, userId: Long) { prefs(c).edit().putLong("user_id", userId).apply() }
    fun disable(c: Context) { prefs(c).edit().clear().apply() }

    @RequiresApi(28)
    fun prompt(activity: Activity, title: String, onSuccess: () -> Unit, onFail: (String) -> Unit) {
        val executor = activity.mainExecutor
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setNegativeButton("Cancel", executor) { _, _ -> onFail("Cancelled") }
            .build()
        prompt.authenticate(CancellationSignal(), executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { onSuccess() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                onFail(errString?.toString() ?: "Fingerprint error")
            }
        })
    }
}
