package com.pettycash.manager.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.pettycash.manager.data.model.Transaction
import com.pettycash.manager.data.model.TransactionType
import com.pettycash.manager.data.model.User

// ─── Transaction DAO ─────────────────────────────────────────────────────────
@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE companyId = :companyId ORDER BY date DESC, createdAt DESC")
    fun getAllTransactions(companyId: String): LiveData<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE companyId = :companyId AND type = :type ORDER BY date DESC")
    fun getTransactionsByType(companyId: String, type: TransactionType): LiveData<List<Transaction>>

    @Query("""
        SELECT * FROM transactions 
        WHERE companyId = :companyId 
        AND date BETWEEN :startDate AND :endDate 
        ORDER BY date DESC
    """)
    fun getTransactionsByDateRange(
        companyId: String,
        startDate: String,
        endDate: String
    ): LiveData<List<Transaction>>

    @Query("""
        SELECT * FROM transactions 
        WHERE companyId = :companyId 
        AND (:category = '' OR category = :category)
        AND (:type IS NULL OR type = :type)
        AND date BETWEEN :startDate AND :endDate 
        ORDER BY date DESC
    """)
    fun getFilteredTransactions(
        companyId: String,
        category: String,
        type: TransactionType?,
        startDate: String,
        endDate: String
    ): LiveData<List<Transaction>>

    @Query("SELECT SUM(amount) FROM transactions WHERE companyId = :companyId AND type = 'CASH_IN'")
    suspend fun getTotalCashIn(companyId: String): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE companyId = :companyId AND type = 'EXPENSE' AND (approvalStatus = 'APPROVED' OR approvalStatus = 'NOT_REQUIRED')")
    suspend fun getTotalExpense(companyId: String): Double?

    @Query("SELECT COUNT(*) FROM transactions WHERE companyId = :companyId AND approvalStatus = 'PENDING'")
    suspend fun getPendingApprovalCount(companyId: String): Int

    @Query("SELECT * FROM transactions WHERE isSynced = 0 AND companyId = :companyId")
    suspend fun getUnsyncedTransactions(companyId: String): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("UPDATE transactions SET isSynced = 1 WHERE transactionId = :transactionId")
    suspend fun markAsSynced(transactionId: String)

    @Query("DELETE FROM transactions WHERE companyId = :companyId")
    suspend fun deleteAllForCompany(companyId: String)

    @Query("SELECT DISTINCT category FROM transactions WHERE companyId = :companyId AND type = 'EXPENSE'")
    suspend fun getExpenseCategories(companyId: String): List<String>
}

// ─── User DAO ─────────────────────────────────────────────────────────────────
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE companyId = :companyId")
    fun getUsersForCompany(companyId: String): LiveData<List<User>>

    @Query("SELECT * FROM users WHERE username = :username AND companyId = :companyId LIMIT 1")
    suspend fun getUserByUsername(username: String, companyId: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Update
    suspend fun updateUser(user: User)
}

// ─── Room Database ────────────────────────────────────────────────────────────
@Database(
    entities = [Transaction::class, User::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun userDao(): UserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "petty_cash_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ─── Type Converters ──────────────────────────────────────────────────────────
class Converters {
    @TypeConverter
    fun fromTransactionType(type: com.pettycash.manager.data.model.TransactionType): String = type.name

    @TypeConverter
    fun toTransactionType(type: String): com.pettycash.manager.data.model.TransactionType =
        com.pettycash.manager.data.model.TransactionType.valueOf(type)

    @TypeConverter
    fun fromApprovalStatus(status: com.pettycash.manager.data.model.ApprovalStatus): String = status.name

    @TypeConverter
    fun toApprovalStatus(status: String): com.pettycash.manager.data.model.ApprovalStatus =
        com.pettycash.manager.data.model.ApprovalStatus.valueOf(status)
}
