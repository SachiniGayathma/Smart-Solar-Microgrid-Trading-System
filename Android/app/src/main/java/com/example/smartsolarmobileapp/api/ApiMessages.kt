/**
 * Reads the message field from a Web API error body.
 */
package com.example.smartsolarmobileapp.api

import com.google.gson.JsonParser
import retrofit2.Response

object ApiMessages {

    fun from(response: Response<*>, fallback: String): String {
        val raw = try {
            response.errorBody()?.string()
        } catch (e: Exception) {
            null
        }
        if (raw.isNullOrBlank()) {
            return fallback
        }
        return try {
            val message = JsonParser.parseString(raw).asJsonObject.get("message")?.asString
            if (message.isNullOrBlank()) raw else message
        } catch (e: Exception) {
            raw
        }
    }
}
