package com.pettycash.manager.data.remote

import com.pettycash.manager.data.model.ApiResponse
import com.pettycash.manager.data.model.DashboardSummary
import com.pettycash.manager.data.model.LoginResponse
import com.pettycash.manager.data.model.Transaction
import com.pettycash.manager.data.model.User
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

// ─── API Service Interface ────────────────────────────────────────────────────
interface ApiService {

    // Master Script Endpoints
    @GET
    suspend fun setupCompany(
        @Url url: String,
        @Query("action") action: String = "setupCompany",
        @Query("companyName") companyName: String,
        @Query("adminUsername") adminUsername: String,
        @Query("adminPasswordHash") adminPasswordHash: String,
        @Query("logoUrl") logoUrl: String = ""
    ): Response<ApiResponse<Map<String, String>>>

    @GET
    suspend fun login(
        @Url url: String,
        @Query("action") action: String = "login",
        @Query("username") username: String,
        @Query("passwordHash") passwordHash: String,
        @Query("companyId") companyId: String
    ): Response<LoginResponse>

    @GET
    suspend fun getSheetId(
        @Url url: String,
        @Query("action") action: String = "getSheetId",
        @Query("companyId") companyId: String
    ): Response<ApiResponse<Map<String, String>>>

    // Company Script Endpoints
    @GET
    suspend fun getTransactions(
        @Url url: String,
        @Query("action") action: String = "getTransactions",
        @Query("companyId") companyId: String,
        @Query("startDate") startDate: String = "",
        @Query("endDate") endDate: String = "",
        @Query("category") category: String = ""
    ): Response<ApiResponse<List<Transaction>>>

    @FormUrlEncoded
    @POST
    suspend fun addTransaction(
        @Url url: String,
        @Field("action") action: String = "addTransaction",
        @Field("companyId") companyId: String,
        @Field("transactionId") transactionId: String,
        @Field("type") type: String,
        @Field("amount") amount: Double,
        @Field("date") date: String,
        @Field("category") category: String,
        @Field("description") description: String,
        @Field("billImageUrl") billImageUrl: String = "",
        @Field("addedBy") addedBy: String
    ): Response<ApiResponse<Transaction>>

    @FormUrlEncoded
    @POST
    suspend fun updateApprovalStatus(
        @Url url: String,
        @Field("action") action: String = "updateApproval",
        @Field("companyId") companyId: String,
        @Field("transactionId") transactionId: String,
        @Field("status") status: String,
        @Field("approvedBy") approvedBy: String
    ): Response<ApiResponse<Unit>>

    @GET
    suspend fun getDashboard(
        @Url url: String,
        @Query("action") action: String = "getDashboard",
        @Query("companyId") companyId: String
    ): Response<ApiResponse<DashboardSummary>>

    @GET
    suspend fun getUsers(
        @Url url: String,
        @Query("action") action: String = "getUsers",
        @Query("companyId") companyId: String
    ): Response<ApiResponse<List<User>>>

    @FormUrlEncoded
    @POST
    suspend fun addUser(
        @Url url: String,
        @Field("action") action: String = "addUser",
        @Field("companyId") companyId: String,
        @Field("userId") userId: String,
        @Field("username") username: String,
        @Field("passwordHash") passwordHash: String,
        @Field("role") role: String
    ): Response<ApiResponse<User>>

    @FormUrlEncoded
    @POST
    suspend fun uploadImage(
        @Url url: String,
        @Field("action") action: String = "uploadImage",
        @Field("companyId") companyId: String,
        @Field("imageBase64") imageBase64: String,
        @Field("fileName") fileName: String
    ): Response<ApiResponse<Map<String, String>>>

    @GET
    suspend fun exportData(
        @Url url: String,
        @Query("action") action: String = "exportData",
        @Query("companyId") companyId: String,
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String
    ): Response<ApiResponse<List<Transaction>>>
}

// ─── Network Client ───────────────────────────────────────────────────────────
object NetworkClient {

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (com.pettycash.manager.BuildConfig.DEBUG)
            HttpLoggingInterceptor.Level.BODY
        else
            HttpLoggingInterceptor.Level.NONE
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // We use a dummy base URL because we use @Url for dynamic URLs
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://script.google.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)
}
