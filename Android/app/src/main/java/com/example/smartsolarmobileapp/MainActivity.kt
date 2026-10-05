/**
 * Application entry point routing to ProsumerDashboardActivity or LoginActivity based on active session.
 */
package com.example.smartsolarmobileapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smartsolarmobileapp.prosumer.LoginActivity
import com.example.smartsolarmobileapp.utils.RoleRouter
import com.example.smartsolarmobileapp.utils.SessionManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionManager = SessionManager(this)
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, RoleRouter.homeActivity(sessionManager.getUserRole())))
        } else {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }
}