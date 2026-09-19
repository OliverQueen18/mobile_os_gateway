package com.osgateway.shared.api

import com.osgateway.shared.model.ApiResponse
import com.osgateway.shared.model.AppUpdateInfo
import com.osgateway.shared.model.AttachmentDto
import com.osgateway.shared.model.ChangePasswordRequest
import com.osgateway.shared.model.ChangePinRequest
import com.osgateway.shared.model.CommissionReportDto
import com.osgateway.shared.model.DistributorMeDto
import com.osgateway.shared.model.ForgotPasswordRequest
import com.osgateway.shared.model.ForgotPasswordResponse
import com.osgateway.shared.model.GatewayDeviceDto
import com.osgateway.shared.model.GatewayRegisterRequest
import com.osgateway.shared.model.GatewayTask
import com.osgateway.shared.model.HeartbeatRequest
import com.osgateway.shared.model.HeartbeatResponse
import com.osgateway.shared.model.LoginRequest
import com.osgateway.shared.model.NotificationDto
import com.osgateway.shared.model.OperationTypeDto
import com.osgateway.shared.model.OperatorDto
import com.osgateway.shared.model.PageResponse
import com.osgateway.shared.model.RefreshRequest
import com.osgateway.shared.model.RegisterPushDeviceRequest
import com.osgateway.shared.model.RegisterPushDeviceResponse
import com.osgateway.shared.model.RegisterRequest
import com.osgateway.shared.model.RegistrationFeePaymentRequest
import com.osgateway.shared.model.RegistrationInfoDto
import com.osgateway.shared.model.RegistrationKycRequest
import com.osgateway.shared.model.ResetPasswordRequest
import com.osgateway.shared.model.SmsReportRequest
import com.osgateway.shared.model.StatsDto
import com.osgateway.shared.model.TaskResultRequest
import com.osgateway.shared.model.TokenResponse
import com.osgateway.shared.model.TransactionDto
import com.osgateway.shared.model.TransactionRequest
import com.osgateway.shared.model.UserProfile
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<TokenResponse>

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): ApiResponse<TokenResponse>

    @GET("auth/registration-info")
    suspend fun registrationInfo(): ApiResponse<RegistrationInfoDto>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): ApiResponse<ForgotPasswordResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): ApiResponse<Map<String, String>?>

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): ApiResponse<TokenResponse>

    @POST("auth/logout")
    suspend fun logout(): ApiResponse<Map<String, String>>
}

interface GatewayApi {
    @POST("gateways")
    suspend fun register(@Body body: GatewayRegisterRequest): ApiResponse<GatewayDeviceDto>

    @POST("gateways/{id}/heartbeat")
    suspend fun heartbeat(
        @Path("id") gatewayId: String,
        @Body body: HeartbeatRequest,
    ): ApiResponse<HeartbeatResponse>

    @GET("gateways/{id}/tasks")
    suspend fun pollTasks(
        @Path("id") gatewayId: String,
        @Query("limit") limit: Int = 5,
    ): ApiResponse<List<GatewayTask>>

    @POST("gateways/{id}/tasks/{taskId}/result")
    suspend fun reportTaskResult(
        @Path("id") gatewayId: String,
        @Path("taskId") taskId: String,
        @Body body: TaskResultRequest,
    ): ApiResponse<Map<String, String>>

    @POST("sms/report")
    suspend fun reportSms(@Body body: SmsReportRequest): ApiResponse<Map<String, String>>

    @GET("apps/gateway/updates")
    suspend fun checkUpdates(
        @Query("versionCode") versionCode: Int,
    ): ApiResponse<AppUpdateInfo>

    @POST("notifications/devices")
    suspend fun registerPushDevice(
        @Body body: RegisterPushDeviceRequest,
    ): ApiResponse<RegisterPushDeviceResponse>

    @DELETE("notifications/devices")
    suspend fun unregisterPushDevice(
        @Query("token") token: String,
    ): ApiResponse<Map<String, String>?>
}

interface TransactionApi {
    @POST("transactions")
    suspend fun create(@Body body: TransactionRequest): ApiResponse<TransactionDto>

    @GET("transactions")
    suspend fun history(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
        @Query("status") status: String? = null,
        @Query("type") type: String? = null,
        @Query("operator") operator: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): ApiResponse<PageResponse<TransactionDto>>

    @GET("transactions/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20,
    ): ApiResponse<PageResponse<TransactionDto>>

    @GET("transactions/{id}")
    suspend fun getById(@Path("id") id: String): ApiResponse<TransactionDto>

    @GET("reports/stats/me")
    suspend fun myStats(): ApiResponse<StatsDto>

    @GET("reports/commissions/me")
    suspend fun myCommissions(
        @Query("from") from: String,
        @Query("to") to: String,
    ): ApiResponse<CommissionReportDto>

    @GET("users/me")
    suspend fun profile(): ApiResponse<UserProfile>

    @POST("users/me/password")
    suspend fun changePassword(@Body body: ChangePasswordRequest): ApiResponse<Map<String, String>?>

    @GET("distributors/me")
    suspend fun myDistributor(): ApiResponse<DistributorMeDto>

    @PUT("distributors/me/registration")
    suspend fun updateMyRegistration(
        @Body body: RegistrationKycRequest,
    ): ApiResponse<DistributorMeDto>

    @POST("distributors/me/registration-fee")
    suspend fun payMyRegistrationFee(
        @Body body: RegistrationFeePaymentRequest,
    ): ApiResponse<DistributorMeDto>

    @GET("distributors/registration-fee")
    suspend fun registrationFee(): ApiResponse<Double>

    @GET("distributors/me/attachments")
    suspend fun myAttachments(): ApiResponse<List<AttachmentDto>>

    @Multipart
    @POST("distributors/me/attachments")
    suspend fun uploadMyAttachment(
        @Query("docType") docType: String,
        @Part file: MultipartBody.Part,
    ): ApiResponse<AttachmentDto>

    @POST("distributors/me/pin")
    suspend fun changePin(@Body body: ChangePinRequest): ApiResponse<Map<String, String>?>

    @GET("operation-types")
    suspend fun operationTypes(
        @Query("active") active: Boolean? = true,
    ): ApiResponse<List<OperationTypeDto>>

    @GET("ussd/operators")
    suspend fun operators(
        @Query("active") active: Boolean? = true,
    ): ApiResponse<List<OperatorDto>>

    @GET("notifications")
    suspend fun notifications(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50,
    ): ApiResponse<PageResponse<NotificationDto>>

    @POST("notifications/devices")
    suspend fun registerPushDevice(
        @Body body: RegisterPushDeviceRequest,
    ): ApiResponse<RegisterPushDeviceResponse>

    @DELETE("notifications/devices")
    suspend fun unregisterPushDevice(
        @Query("token") token: String,
    ): ApiResponse<Map<String, String>?>

    @GET("apps/distributor/updates")
    suspend fun checkUpdates(
        @Query("versionCode") versionCode: Int,
    ): ApiResponse<AppUpdateInfo>
}
