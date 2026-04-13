package com.pettycash.manager.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferenceManager private constructor(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREF_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val PREF_FILE_NAME = "petty_cash_secure_prefs"

        // Keys
        private const val KEY_IS_COMPANY_SETUP = "is_company_setup"
        private const val KEY_COMPANY_ID = "company_id"
        private const val KEY_COMPANY_NAME = "company_name"
        private const val KEY_SHEET_ID = "sheet_id"
        private const val KEY_LOGO_URL = "logo_url"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_USER_ROLE = "user_role"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_APPROVAL_ENABLED = "approval_enabled"
        private const val KEY_LAST_SYNC = "last_sync"

        @Volatile
        private var INSTANCE: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return INSTANCE ?: synchronized(this) {
                PreferenceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ─── Company Setup ───────────────────────────────────────────────────────
    var isCompanySetup: Boolean
        get() = prefs.getBoolean(KEY_IS_COMPANY_SETUP, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_COMPANY_SETUP, value).apply()

    var companyId: String
        get() = prefs.getString(KEY_COMPANY_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COMPANY_ID, value).apply()

    var companyName: String
        get() = prefs.getString(KEY_COMPANY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COMPANY_NAME, value).apply()

    var sheetId: String
        get() = prefs.getString(KEY_SHEET_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SHEET_ID, value).apply()

    var logoUrl: String
        get() = prefs.getString(KEY_LOGO_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LOGO_URL, value).apply()

    // ─── User Session ────────────────────────────────────────────────────────
    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    var userRole: String
        get() = prefs.getString(KEY_USER_ROLE, "employee") ?: "employee"
        set(value) = prefs.edit().putString(KEY_USER_ROLE, value).apply()

    // ─── App Settings ────────────────────────────────────────────────────────
    var isApprovalEnabled: Boolean
        get() = prefs.getBoolean(KEY_APPROVAL_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_APPROVAL_ENABLED, value).apply()

    var lastSyncTime: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    fun saveSession(
        userId: String,
        username: String,
        role: String,
        companyId: String,
        companyName: String,
        sheetId: String,
        logoUrl: String
    ) {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, true)
            putString(KEY_USER_ID, userId)
            putString(KEY_USERNAME, username)
            putString(KEY_USER_ROLE, role)
            putString(KEY_COMPANY_ID, companyId)
            putString(KEY_COMPANY_NAME, companyName)
            putString(KEY_SHEET_ID, sheetId)
            putString(KEY_LOGO_URL, logoUrl)
            apply()
        }
    }

    fun clearSession() {
        prefs.edit().apply {
            putBoolean(KEY_IS_LOGGED_IN, false)
            putString(KEY_USER_ID, "")
            putString(KEY_USERNAME, "")
            putString(KEY_USER_ROLE, "")
            apply()
        }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    val isAdmin: Boolean get() = userRole == "admin"
    val isManager: Boolean get() = userRole == "admin" || userRole == "manager"
}
