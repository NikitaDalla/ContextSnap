package com.example.contextsnap

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// Step 1: email + security answer. Step 2: set a new password.
class ForgotPasswordActivity : AppCompatActivity() {

    private var verifiedUser: UserRow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val emailField = findViewById<EditText>(R.id.editEmail)
        val answerField = findViewById<EditText>(R.id.editAnswer)
        val error1 = findViewById<TextView>(R.id.textError1)
        val step2 = findViewById<View>(R.id.layoutStep2)
        val newPasswordField = findViewById<EditText>(R.id.editNewPassword)
        val confirmField = findViewById<EditText>(R.id.editConfirmNew)
        val error2 = findViewById<TextView>(R.id.textError2)

        findViewById<Button>(R.id.buttonVerify).setOnClickListener {
            val user = Db.get(this).getUserByEmail(emailField.text.toString().trim())
            val answer = answerField.text.toString().trim().lowercase()
            if (user == null || !Passwords.verify(answer, user.answerHash)) {
                error1.text = "Email or security answer is incorrect"
                return@setOnClickListener
            }
            verifiedUser = user
            error1.text = ""
            step2.visibility = View.VISIBLE
        }

        findViewById<Button>(R.id.buttonReset).setOnClickListener {
            val user = verifiedUser
            if (user == null) {
                return@setOnClickListener
            }
            val newPassword = newPasswordField.text.toString()
            if (!Passwords.isStrong(newPassword)) {
                error2.text = "Password needs 8+ characters with a letter and a number"
                return@setOnClickListener
            }
            if (newPassword != confirmField.text.toString()) {
                error2.text = "Passwords do not match"
                return@setOnClickListener
            }
            Db.get(this).updatePassword(user.id, Passwords.hash(newPassword))
            Toast.makeText(this, "Password updated. Please log in.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
