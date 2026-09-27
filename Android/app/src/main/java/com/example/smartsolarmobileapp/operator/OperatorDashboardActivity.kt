/**
 * Grid Operator home. Shows live booking counts and opens scan, map, and reservations.
 */
package com.example.smartsolarmobileapp.operator

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartsolarmobileapp.R
import com.example.smartsolarmobileapp.prosumer.LoginActivity
import com.example.smartsolarmobileapp.utils.RoleRouter
import com.example.smartsolarmobileapp.utils.SessionManager
import kotlinx.coroutines.launch

class OperatorDashboardActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var repository: OperatorRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
        if (!sessionManager.isLoggedIn()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        if (!sessionManager.isOperator()) {
            startActivity(Intent(this, RoleRouter.homeActivity(sessionManager.getUserRole())))
            finish()
            return
        }

        setContentView(R.layout.activity_operator_dashboard)
        repository = OperatorRepository(this)

        findViewById<TextView>(R.id.tv_operator_welcome)?.text =
            "Welcome, ${sessionManager.getUserFullName()?.ifBlank { null } ?: "Operator"}"
        findViewById<TextView>(R.id.tv_operator_role)?.text =
            sessionManager.getUser()?.role ?: RoleRouter.ROLE_GRID_OPERATOR

        findViewById<android.view.View>(R.id.btn_operator_logout).setOnClickListener {
            sessionManager.clearSession()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
        findViewById<android.view.View>(R.id.card_scan_qr).setOnClickListener {
            startActivity(Intent(this, QrScanActivity::class.java))
        }
        findViewById<android.view.View>(R.id.card_view_map).setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        findViewById<android.view.View>(R.id.card_view_bookings).setOnClickListener {
            startActivity(Intent(this, ReservationListActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::repository.isInitialized) return
        lifecycleScope.launch {
            try {
                when (val result = repository.loadDashboard()) {
                    is OperatorLoad.Fresh -> bindCounts(result.data)
                    is OperatorLoad.Cached -> bindCounts(result.data)
                    is OperatorLoad.Failed -> {
                        Toast.makeText(this@OperatorDashboardActivity, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@OperatorDashboardActivity,
                    e.message ?: "Could not load operator dashboard.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun bindCounts(dashboard: com.example.smartsolarmobileapp.models.ReservationDashboard) {
        findViewById<TextView>(R.id.tv_pending_value)?.text = dashboard.pendingCount.toString()
        findViewById<TextView>(R.id.tv_approved_value)?.text = dashboard.approvedFutureCount.toString()
        findViewById<TextView>(R.id.tv_current_value)?.text = dashboard.currentCount.toString()
        findViewById<TextView>(R.id.tv_history_value)?.text = dashboard.historyCount.toString()
    }
}
