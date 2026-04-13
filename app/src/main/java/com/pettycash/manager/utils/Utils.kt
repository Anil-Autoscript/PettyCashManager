package com.pettycash.manager.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.security.MessageDigest
import java.text.NumberFormat
import java.util.Locale

// ─── Result Sealed Class ──────────────────────────────────────────────────────
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

// ─── Hash Utilities ───────────────────────────────────────────────────────────
object HashUtils {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

// ─── Network Utilities ────────────────────────────────────────────────────────
object NetworkUtils {
    fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

// ─── Currency Formatter ───────────────────────────────────────────────────────
object CurrencyUtils {
    fun format(amount: Double, currencySymbol: String = "₹"): String {
        val format = NumberFormat.getNumberInstance(Locale("en", "IN"))
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        return "$currencySymbol ${format.format(amount)}"
    }
}

// ─── Date Utilities ───────────────────────────────────────────────────────────
object DateUtils {
    fun getCurrentDate(): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    fun formatForDisplay(dateStr: String): String {
        return try {
            val input = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val output = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            output.format(input.parse(dateStr) ?: java.util.Date())
        } catch (_: Exception) { dateStr }
    }

    fun getFirstDayOfMonth(): String {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }

    fun getLastDayOfMonth(): String {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.DAY_OF_MONTH, cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH))
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }
}

// ─── Image Utilities ──────────────────────────────────────────────────────────
object ImageUtils {
    fun fileToBase64(file: java.io.File): String {
        val bytes = file.readBytes()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
    }

    fun uriToBase64(context: Context, uri: android.net.Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bytes = inputStream?.readBytes() ?: return ""
        inputStream.close()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
    }
}
