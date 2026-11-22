package com.example.contextsnap

import android.content.Context
import android.os.Build
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Password hashing with PBKDF2 (built into Android, so no extra library is needed).
// A random salt is generated for every password; the plain password is never stored.
object Passwords {
    private const val ITERATIONS = 60000

    fun isStrong(p: String) = p.length >= 8 && p.any { it.isDigit() } && p.any { it.isLetter() }

    fun hash(plain: String): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val algo = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val h = derive(plain, salt, ITERATIONS, algo)
        return listOf(
            algo, ITERATIONS.toString(),
            Base64.encodeToString(salt, Base64.NO_WRAP),
            Base64.encodeToString(h, Base64.NO_WRAP)
        ).joinToString(":")
    }

    fun verify(plain: String, stored: String): Boolean {
        val p = stored.split(":")
        if (p.size != 4) return false
        val salt = Base64.decode(p[2], Base64.NO_WRAP)
        val expected = Base64.decode(p[3], Base64.NO_WRAP)
        return MessageDigest.isEqual(derive(plain, salt, p[1].toInt(), p[0]), expected)
    }

    private fun derive(plain: String, salt: ByteArray, iterations: Int, algo: String): ByteArray {
        val spec = PBEKeySpec(plain.toCharArray(), salt, iterations, 256)
        return SecretKeyFactory.getInstance(algo).generateSecret(spec).encoded
    }
}

// Remembers who is logged in until Logout is pressed.
object SessionStore {
    private fun prefs(c: Context) = c.getSharedPreferences("session", Context.MODE_PRIVATE)

    fun login(c: Context, userId: Long) { prefs(c).edit().putLong("user_id", userId).apply() }

    fun logout(c: Context) {
        prefs(c).edit().clear().apply()
        AppState.activeSessionId = -1L
    }

    fun userId(c: Context): Long = prefs(c).getLong("user_id", -1L)

    fun isLoggedIn(c: Context): Boolean = userId(c) != -1L
}
