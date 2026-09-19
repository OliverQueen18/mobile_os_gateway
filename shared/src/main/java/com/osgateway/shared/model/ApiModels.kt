package com.osgateway.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean = true,
    val message: String? = null,
    val errorCode: String? = null,
    val data: T? = null,
    val timestamp: String? = null,
)

@Serializable
data class PageResponse<T>(
    val content: List<T> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Long = 0,
    val totalPages: Int = 0,
    val first: Boolean = true,
    val last: Boolean = true,
)

@Serializable
data class LoginRequest(
    /** Username ou numéro de téléphone. */
    val username: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
    val fullName: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rccm: String? = null,
    val nif: String? = null,
    /** N° biométrique ou NINA */
    val nina: String? = null,
)

@Serializable
data class RegistrationInfoDto(
    val registrationFee: Double? = null,
    val termsOfUse: String? = null,
    val currency: String? = null,
)

@Serializable
data class ForgotPasswordRequest(
    val email: String,
)

@Serializable
data class ForgotPasswordResponse(
    val message: String? = null,
    val resetCode: String? = null,
)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val newPassword: String,
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long? = null,
    val expiresAt: String? = null,
    val userId: Long? = null,
    val username: String? = null,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val gatewayId: String? = null,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class OperationTypeDto(
    val id: Long? = null,
    val code: String,
    val label: String,
    val description: String? = null,
    val icon: String? = null,
    val balanceEffect: String? = null,
    val commissionMode: String? = null,
    val commissionValue: Double? = null,
    val active: Boolean = true,
    val cancellable: Boolean = true,
    val requiresPhone: Boolean = true,
    val requiresAmount: Boolean = true,
)

@Serializable
data class DistributorMeDto(
    val id: Long? = null,
    val userId: Long? = null,
    val code: String? = null,
    val name: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rccm: String? = null,
    val nif: String? = null,
    val nina: String? = null,
    val balance: Double? = null,
    val commissionRate: Double? = null,
    val active: Boolean = true,
    val username: String? = null,
    val hasPin: Boolean = false,
    /** FEE_PENDING | UNDER_REVIEW | APPROVED | REJECTED */
    val registrationStatus: String? = null,
    val registrationFeeAmount: Double? = null,
    val registrationFeePaid: Boolean = false,
    val registrationFeePaidAt: String? = null,
    val registrationFeePaymentRef: String? = null,
    val registrationFeePaymentMethod: String? = null,
    val rejectionReason: String? = null,
    val reviewedAt: String? = null,
    val submittedAt: String? = null,
    val attachmentCount: Int = 0,
) {
    fun isRegistrationApproved(): Boolean =
        registrationStatus.isNullOrBlank() ||
            registrationStatus.equals("APPROVED", ignoreCase = true)

    fun canOperate(): Boolean = active && isRegistrationApproved()
}

@Serializable
data class AttachmentDto(
    val id: Long? = null,
    val distributorId: Long? = null,
    val docType: String? = null,
    val fileName: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
    val createdAt: String? = null,
)

@Serializable
data class RegistrationKycRequest(
    val rccm: String? = null,
    val nif: String? = null,
    val nina: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val phone: String? = null,
    val name: String? = null,
)

@Serializable
data class RegistrationFeePaymentRequest(
    val amount: Double,
    /** CASH | BANK_TRANSFER | MOBILE_MONEY | OTHER */
    val paymentMethod: String,
    val reference: String? = null,
    val note: String? = null,
)

@Serializable
data class OperatorDto(
    val id: Long? = null,
    val code: String,
    val name: String? = null,
    val active: Boolean = true,
    val logoUrl: String? = null,
)

@Serializable
data class ChangePinRequest(
    val currentPin: String,
    val newPin: String,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

enum class TransactionType {
    DEPOT, RETRAIT, TRANSFERT, SOLDE, ACHAT_CREDIT, PAIEMENT, ACHAT_UV
}

enum class TransactionStatus {
    PENDING, QUEUED, ASSIGNED, PROCESSING, WAITING_SMS_CONFIRMATION, SUCCESS, FAILED, TIMEOUT, CANCELLED
}

enum class GatewayStatus {
    ONLINE, OFFLINE, BUSY, MAINTENANCE, DISABLED
}

enum class UssdStepAction {
    COMPOSE, READ, REPLY, WAIT, CONTINUE, VALIDATE, EXTRACT, WAIT_SMS, RUN_TEMPLATE, VERIFY_BALANCE
}

@Serializable
data class HeartbeatRequest(
    val battery: Int = 0,
    /** Force réseau 0–100 (attendu par le backend). */
    val network: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val memory: Long? = null,
    val storage: Long? = null,
    val temp: Double? = null,
    val internet: Boolean? = true,
    val networkType: String? = null,
    val imei: String? = null,
    val androidVersion: String? = null,
    val appVersion: String? = null,
    val simOperator: String? = null,
)

@Serializable
data class HeartbeatResponse(
    val id: Long? = null,
    val deviceId: String? = null,
    val status: String? = null,
    val batteryLevel: Int? = null,
    val networkStrength: Int? = null,
    val lastHeartbeatAt: String? = null,
    val nextPollSeconds: Int = 30,
    val pendingTasks: Int = 0,
)

@Serializable
data class GatewayRegisterRequest(
    val deviceId: String,
    val name: String,
    val operator: String,
    val phoneNumber: String? = null,
    val apiKey: String? = null,
)

@Serializable
data class GatewayDeviceDto(
    val id: Long,
    val deviceId: String? = null,
    val name: String? = null,
    val operator: String? = null,
    val phoneNumber: String? = null,
    val status: String? = null,
)

@Serializable
data class BalancePattern(
    val fieldType: String,
    val regexPattern: String,
    val priority: Int = 10,
)

@Serializable
data class GatewayTask(
    val id: String,
    val type: String,
    val operator: String? = null,
    val transactionId: String? = null,
    val phone: String? = null,
    val amount: Double? = null,
    val pin: String? = null,
    val priority: Int = 5,
    val ussdCode: String? = null,
    val smsBody: String? = null,
    val smsTo: String? = null,
    val steps: List<UssdStep> = emptyList(),
    val variables: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int = 120,
    /** Étapes USSD SOLDE (consultation solde gateway). */
    val balanceCheckSteps: List<UssdStep> = emptyList(),
    /** Motifs regex extraction solde (paramétrables par opérateur). */
    val balancePatterns: List<BalancePattern> = emptyList(),
)

@Serializable
data class UssdStep(
    val order: Int = 0,
    val action: String,
    val value: String? = null,
    val expectedPattern: String? = null,
    val timeoutMs: Long = 15_000,
    val variableName: String? = null,
    val clickLabel: String? = null,
    /** Étapes du modèle appelé (RUN_TEMPLATE). */
    val nestedSteps: List<UssdStep> = emptyList(),
)

@Serializable
data class TaskResultRequest(
    val taskId: String,
    val status: String,
    val ussdResponse: String? = null,
    val extracted: Map<String, String> = emptyMap(),
    val durationMs: Long? = null,
    val errorMessage: String? = null,
    val screenshotBase64: String? = null,
    val stepLogs: List<StepLog> = emptyList(),
)

@Serializable
data class StepLog(
    val order: Int,
    val action: String,
    val success: Boolean,
    val detail: String? = null,
    val windowText: String? = null,
)

@Serializable
data class SmsReportRequest(
    val direction: String,
    val address: String,
    val body: String,
    val timestamp: Long,
    val taskId: String? = null,
    val status: String? = null,
)

@Serializable
data class TransactionRequest(
    val type: String,
    val operator: String,
    val beneficiaryPhone: String? = null,
    val amount: Double? = null,
    val note: String? = null,
    val priority: String = "NORMAL",
    /** PIN transaction du distributeur (4–6 chiffres). */
    val pin: String? = null,
)

@Serializable
data class TransactionDto(
    val id: String? = null,
    val reference: String? = null,
    val type: String? = null,
    val operator: String? = null,
    val beneficiaryPhone: String? = null,
    val amount: Double? = null,
    val commission: Double? = null,
    val adminCommission: Double? = null,
    val distributorCommission: Double? = null,
    val status: String? = null,
    val ussdResponse: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val duration: Long? = null,
)

@Serializable
data class StatsDto(
    val totalToday: Long = 0,
    val successToday: Long = 0,
    val failedToday: Long = 0,
    val volumeToday: Double = 0.0,
    val commissionToday: Double = 0.0,
    val byType: Map<String, Long> = emptyMap(),
    val byOperator: Map<String, Long> = emptyMap(),
)

@Serializable
data class CommissionReportDto(
    val from: String? = null,
    val to: String? = null,
    val rows: List<CommissionReportRow> = emptyList(),
    val totals: CommissionTotals? = null,
)

@Serializable
data class CommissionReportRow(
    val distributor_id: Long? = null,
    val distributor_code: String? = null,
    val distributor_name: String? = null,
    val operator: String? = null,
    val type: String? = null,
    val count: Long? = null,
    val total_amount: Double? = null,
    val total_commission: Double? = null,
    val total_admin_commission: Double? = null,
    val total_distributor_commission: Double? = null,
)

@Serializable
data class CommissionTotals(
    val totalCommission: Double? = null,
    val totalAdminCommission: Double? = null,
    val totalDistributorCommission: Double? = null,
)

@Serializable
data class UserProfile(
    val id: String? = null,
    val username: String? = null,
    val fullName: String? = null,
    val phone: String? = null,
    val roles: List<String> = emptyList(),
    val operator: String? = null,
)

@Serializable
data class AppUpdateInfo(
    val latestVersionCode: Int = 0,
    val latestVersionName: String? = null,
    val downloadUrl: String? = null,
    val mandatory: Boolean = false,
    val releaseNotes: String? = null,
)

@Serializable
data class NotificationDto(
    val id: String,
    val title: String,
    val body: String,
    val read: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
data class RegisterPushDeviceRequest(
    val token: String,
    val platform: String = "ANDROID",
    /** DISTRIBUTOR | GATEWAY */
    val app: String,
    val gatewayId: Long? = null,
)

@Serializable
data class RegisterPushDeviceResponse(
    val id: Long? = null,
    val userId: Long? = null,
    val app: String? = null,
    val platform: String? = null,
    val gatewayId: Long? = null,
)
