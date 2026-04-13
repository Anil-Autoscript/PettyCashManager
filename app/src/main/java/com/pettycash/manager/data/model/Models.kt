package com.pettycash.manager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── Company Model ───────────────────────────────────────────────────────────
data class Company(
    val companyId: String,
    val companyName: String,
    val sheetId: String,
    val logoUrl: String = ""
)

// ─── User Model ──────────────────────────────────────────────────────────────
@Entity(tableName = "users")
data class User(
    @PrimaryKey val userId: String,
    val companyId: String,
    val username: String,
    val passwordHash: String,
    val role: String = "employee",   // admin | manager | employee
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

// ─── Transaction Model ───────────────────────────────────────────────────────
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey val transactionId: String,
    val companyId: String,
    val type: TransactionType,          // EXPENSE | CASH_IN
    val amount: Double,
    val date: String,                   // "yyyy-MM-dd"
    val category: String,
    val description: String,
    val billImageUrl: String = "",      // Google Drive URL
    val addedBy: String,
    val approvalStatus: ApprovalStatus = ApprovalStatus.NOT_REQUIRED,
    val approvedBy: String = "",
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TransactionType { EXPENSE, CASH_IN }

enum class ApprovalStatus { NOT_REQUIRED, PENDING, APPROVED, REJECTED }

// ─── Session / Login Response ────────────────────────────────────────────────
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val userId: String = "",
    val username: String = "",
    val role: String = "",
    val companyId: String = "",
    val companyName: String = "",
    val sheetId: String = "",
    val logoUrl: String = ""
)

// ─── API Response Wrapper ────────────────────────────────────────────────────
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
)

// ─── Dashboard Summary ───────────────────────────────────────────────────────
data class DashboardSummary(
    val totalCashIn: Double,
    val totalExpense: Double,
    val currentBalance: Double,
    val pendingApprovals: Int,
    val transactionCount: Int
)

// ─── Setup Request ───────────────────────────────────────────────────────────
data class CompanySetupRequest(
    val companyName: String,
    val adminUsername: String,
    val adminPasswordHash: String,
    val logoUrl: String = ""
)
