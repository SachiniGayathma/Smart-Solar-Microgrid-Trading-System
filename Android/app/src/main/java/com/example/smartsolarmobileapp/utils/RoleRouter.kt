/**
 * Sends each logged-in role to the correct home screen.
 */
package com.example.smartsolarmobileapp.utils

import android.app.Activity
import com.example.smartsolarmobileapp.operator.OperatorDashboardActivity
import com.example.smartsolarmobileapp.prosumer.ProsumerDashboardActivity

object RoleRouter {

    const val ROLE_OPERATOR = "Operator"
    const val ROLE_GRID_OPERATOR = "GridOperator"
    const val ROLE_BACKOFFICE = "Backoffice"

    fun isOperatorRole(role: String?): Boolean {
        val normalized = role?.trim()?.replace(" ", "").orEmpty()
        return normalized.equals(ROLE_OPERATOR, ignoreCase = true) ||
            normalized.equals(ROLE_GRID_OPERATOR, ignoreCase = true) ||
            normalized.equals(ROLE_BACKOFFICE, ignoreCase = true)
    }

    fun homeActivity(role: String?): Class<out Activity> {
        return if (isOperatorRole(role)) {
            OperatorDashboardActivity::class.java
        } else {
            ProsumerDashboardActivity::class.java
        }
    }
}
