package com.example.contextsnap

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Already logged in? Skip straight to the dashboard.
        if (SessionStore.isLoggedIn(this)) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_login)

        val emailField = findViewById<EditText>(R.id.editEmail)
        val passwordField = findViewById<EditText>(R.id.editPassword)
        val errorText = findViewById<TextView>(R.id.textError)

        findViewById<Button>(R.id.buttonLogin).setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString()
            if (email.isEmpty() || password.isEmpty()) {
                errorText.text = "Enter email and password"
                return@setOnClickListener
            }
            val user = Db.get(this).getUserByEmail(email)
            // One generic message on purpose: never reveal which part was wrong.
            if (user == null || !Passwords.verify(password, user.passHash)) {
                errorText.text = "Incorrect email or password"
                return@setOnClickListener
            }
            SessionStore.login(this, user.id)
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }

        // Fingerprint quick login: only shown if this phone has a fingerprint sensor
        // AND a user previously turned it on from the dashboard.
        val bioButton = findViewById<Button>(R.id.buttonFingerprint)
        if (Fingerprint.isAvailable(this) && Fingerprint.enabledUser(this) != -1L) {
            bioButton.visibility = View.VISIBLE
            bioButton.setOnClickListener {
                if (Build.VERSION.SDK_INT >= 28) {
                    Fingerprint.prompt(this, "Login with fingerprint",
                        onSuccess = {
                            SessionStore.login(this, Fingerprint.enabledUser(this))
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        },
                        onFail = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() })
                }
            }
        }

        findViewById<TextView>(R.id.textForgot).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
        findViewById<TextView>(R.id.textSignUp).setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
    }
}
