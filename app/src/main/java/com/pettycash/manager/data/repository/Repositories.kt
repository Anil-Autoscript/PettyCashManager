package com.pettycash.manager.data.repository

import android.content.Context
import android.util.Log
import com.pettycash.manager.BuildConfig
import com.pettycash.manager.data.local.AppDatabase
import com.pettycash.manager.data.local.PreferenceManager
import com.pettycash.manager.data.model.*
import com.pettycash.manager.data.remote.NetworkClient
import com.pettycash.manager.utils.HashUtils
import com.pettycash.manager.utils.NetworkUtils
import com.pettycash.manager.utils.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

// ─── Auth Repository ──────────────────────────────────────────────────────────
class AuthRepository(private val context: Context) {

    private val prefs = PreferenceManager.getInstance(context)
    private val db = AppDatabase.getInstance(context)
    private val api = NetworkClient.apiService
    private val masterUrl = BuildConfig.MASTER_SCRIPT_URL

    suspend fun setupCompany(
        companyName: String,
        adminUsername: String,
        adminPassword: String,
        logoUrl: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!NetworkUtils.isConnected(context)) {
                return@withContext Result.Error("No internet connection")
            }
            val passwordHash = HashUtils.sha256(adminPassword)
            val response = api.setupCompany(
                url = masterUrl,
                companyName = companyName,
                adminUsername = adminUsername,
                adminPasswordHash = passwordHash,
                logoUrl = logoUrl
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()!!.data!!
                val companyId = data["companyId"] ?: return@withContext Result.Error("Invalid response")
                prefs.isCompanySetup = true
                prefs.companyId = companyId
                prefs.companyName = companyName
                prefs.sheetId = data["sheetId"] ?: ""
                prefs.logoUrl = logoUrl
                Result.Success(companyId)
            } else {
                Result.Error(response.body()?.message ?: "Setup failed")
            }
        } catch (e: Exception) {
            Log.e("AuthRepo", "setupCompany error", e)
            Result.Error("Network error: ${e.message}")
        }
    }

    suspend fun login(
        username: String,
        password: String,
        companyId: String
    ): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            if (!NetworkUtils.isConnected(context)) {
                // Offline login - check local DB
                val db = AppDatabase.getInstance(context)
                val user = db.userDao().getUserByUsername(username, companyId)
                return@withContext if (user != null && user.passwordHash == HashUtils.sha256(password)) {
                    val response = LoginResponse(
                        success = true,
                        message = "Offline login",
                        userId = user.userId,
                        username = user.username,
                        role = user.role,
                        companyId = user.companyId,
                        companyName = prefs.companyName,
                        sheetId = prefs.sheetId,
                        logoUrl = prefs.logoUrl
                    )
                    prefs.saveSession(
                        userId = user.userId,
                        username = user.username,
                        role = user.role,
                        companyId = user.companyId,
                        companyName = prefs.companyName,
                        sheetId = prefs.sheetId,
                        logoUrl = prefs.logoUrl
                    )
                    Result.Success(response)
                } else {
                    Result.Error("Invalid credentials (offline mode)")
                }
            }

            val passwordHash = HashUtils.sha256(password)
            val response = api.login(
                url = masterUrl,
                username = username,
                passwordHash = passwordHash,
                companyId = companyId
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val loginData = response.body()!!
                prefs.saveSession(
                    userId = loginData.userId,
                    username = loginData.username,
                    role = loginData.role,
                    companyId = loginData.companyId,
                    companyName = loginData.companyName,
                    sheetId = loginData.sheetId,
                    logoUrl = loginData.logoUrl
                )
                // Cache user locally
                db.userDao().insertUser(
                    User(
                        userId = loginData.userId,
                        companyId = loginData.companyId,
                        username = loginData.username,
                        passwordHash = HashUtils.sha256(password),
                        role = loginData.role
                    )
                )
                Result.Success(loginData)
            } else {
                Result.Error(response.body()?.message ?: "Login failed")
            }
        } catch (e: Exception) {
            Log.e("AuthRepo", "login error", e)
            Result.Error("Network error: ${e.message}")
        }
    }

    fun logout() {
        prefs.clearSession()
    }
}

// ─── Transaction Repository ───────────────────────────────────────────────────
class TransactionRepository(private val context: Context) {

    private val prefs = PreferenceManager.getInstance(context)
    private val db = AppDatabase.getInstance(context)
    private val api = NetworkClient.apiService

    private fun getScriptUrl(): String {
        // Each company has its own script URL derived from the master script
        return BuildConfig.MASTER_SCRIPT_URL
    }

    fun getTransactionsLive(companyId: String) =
        db.transactionDao().getAllTransactions(companyId)

    fun getFilteredTransactionsLive(
        companyId: String, category: String,
        type: TransactionType?, startDate: String, endDate: String
    ) = db.transactionDao().getFilteredTransactions(companyId, category, type, startDate, endDate)

    suspend fun syncFromServer(companyId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!NetworkUtils.isConnected(context)) return@withContext Result.Error("No internet")

            val sheetId = prefs.sheetId
            val response = api.getTransactions(
                url = getScriptUrl(),
                companyId = companyId
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val transactions = response.body()!!.data ?: emptyList()
                db.transactionDao().insertTransactions(transactions)
                prefs.lastSyncTime = System.currentTimeMillis()
                Result.Success(transactions.size)
            } else {
                Result.Error("Sync failed: ${response.body()?.message}")
            }
        } catch (e: Exception) {
            Result.Error("Sync error: ${e.message}")
        }
    }

    suspend fun addTransaction(transaction: Transaction): Result<Transaction> = withContext(Dispatchers.IO) {
        // Save locally first
        val localTx = transaction.copy(isSynced = false)
        db.transactionDao().insertTransaction(localTx)

        if (!NetworkUtils.isConnected(context)) {
            return@withContext Result.Success(localTx) // Will sync later
        }

        try {
            val response = api.addTransaction(
                url = getScriptUrl(),
                companyId = transaction.companyId,
                transactionId = transaction.transactionId,
                type = transaction.type.name,
                amount = transaction.amount,
                date = transaction.date,
                category = transaction.category,
                description = transaction.description,
                billImageUrl = transaction.billImageUrl,
                addedBy = transaction.addedBy
            )
            if (response.isSuccessful && response.body()?.success == true) {
                db.transactionDao().markAsSynced(transaction.transactionId)
                Result.Success(transaction.copy(isSynced = true))
            } else {
                Result.Success(localTx) // Saved locally, sync later
            }
        } catch (e: Exception) {
            Result.Success(localTx) // Saved locally, sync later
        }
    }

    suspend fun getDashboardSummary(companyId: String): Result<DashboardSummary> =
        withContext(Dispatchers.IO) {
            try {
                val totalIn = db.transactionDao().getTotalCashIn(companyId) ?: 0.0
                val totalOut = db.transactionDao().getTotalExpense(companyId) ?: 0.0
                val pending = db.transactionDao().getPendingApprovalCount(companyId)
                val summary = DashboardSummary(
                    totalCashIn = totalIn,
                    totalExpense = totalOut,
                    currentBalance = totalIn - totalOut,
                    pendingApprovals = pending,
                    transactionCount = 0
                )
                Result.Success(summary)
            } catch (e: Exception) {
                Result.Error("Error: ${e.message}")
            }
        }

    suspend fun syncUnsyncedTransactions(companyId: String) = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isConnected(context)) return@withContext
        val unsynced = db.transactionDao().getUnsyncedTransactions(companyId)
        unsynced.forEach { tx ->
            try {
                val response = api.addTransaction(
                    url = getScriptUrl(),
                    companyId = tx.companyId,
                    transactionId = tx.transactionId,
                    type = tx.type.name,
                    amount = tx.amount,
                    date = tx.date,
                    category = tx.category,
                    description = tx.description,
                    billImageUrl = tx.billImageUrl,
                    addedBy = tx.addedBy
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    db.transactionDao().markAsSynced(tx.transactionId)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun uploadBillImage(
        companyId: String,
        imageBase64: String,
        fileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.uploadImage(
                url = getScriptUrl(),
                companyId = companyId,
                imageBase64 = imageBase64,
                fileName = fileName
            )
            if (response.isSuccessful && response.body()?.success == true) {
                val url = response.body()!!.data?.get("url") ?: ""
                Result.Success(url)
            } else {
                Result.Error("Upload failed")
            }
        } catch (e: Exception) {
            Result.Error("Upload error: ${e.message}")
        }
    }

    suspend fun getExpenseCategories(companyId: String): List<String> =
        withContext(Dispatchers.IO) {
            val localCategories = db.transactionDao().getExpenseCategories(companyId).toMutableList()
            val defaults = listOf(
                "Office Supplies", "Travel", "Food & Beverages", "Utilities",
                "Maintenance", "Marketing", "Entertainment", "Miscellaneous"
            )
            val merged = (defaults + localCategories).distinct()
            merged
        }
}
