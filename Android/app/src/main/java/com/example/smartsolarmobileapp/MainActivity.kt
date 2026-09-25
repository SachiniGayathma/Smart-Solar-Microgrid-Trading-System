/**
 * Application entry point routing to ProsumerDashboardActivity or LoginActivity based on active session.
 */
package com.example.smartsolarmobileapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smartsolarmobileapp.prosumer.LoginActivity
import com.example.smartsolarmobileapp.prosumer.ProsumerDashboardActivity
import com.example.smartsolarmobileapp.utils.SessionManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, ProsumerDashboardActivity::class.java))
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}