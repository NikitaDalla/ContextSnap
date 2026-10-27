package com.example.contextsnap

import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SignUpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val nameField = findViewById<EditText>(R.id.editName)
        val emailField = findViewById<EditText>(R.id.editEmail)
        val passwordField = findViewById<EditText>(R.id.editPassword)
        val confirmField = findViewById<EditText>(R.id.editConfirm)
        val answerField = findViewById<EditText>(R.id.editAnswer)
        val errorText = findViewById<TextView>(R.id.textError)

        findViewById<Button>(R.id.buttonCreate).setOnClickListener {
            val name = nameField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString()
            val confirm = confirmField.text.toString()
            val answer = answerField.text.toString().trim().lowercase()

            if (name.isEmpty()) {
                errorText.text = "Name is required"
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                errorText.text = "Enter a valid email"
                return@setOnClickListener
            }
            if (!Passwords.isStrong(password)) {
                errorText.text = "Password needs 8+ characters with a letter and a number"
                return@setOnClickListener
            }
            if (password != confirm) {
                errorText.text = "Passwords do not match"
                return@setOnClickListener
            }
            if (answer.isEmpty()) {
                errorText.text = "Security answer is required"
                return@setOnClickListener
            }

            val id = Db.get(this).createUser(name, email, Passwords.hash(password), Passwords.hash(answer))
            if (id == -1L) {
                errorText.text = "An account with this email already exists"
                return@setOnClickListener
            }
            Toast.makeText(this, "Account created. Please log in.", Toast.LENGTH_SHORT).show()
            finish()   // back to the Login screen
        }

        findViewById<TextView>(R.id.textLogin).setOnClickListener { finish() }
    }
}
