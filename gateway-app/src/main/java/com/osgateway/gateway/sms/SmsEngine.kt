package com.osgateway.gateway.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.shared.model.GatewayTask
import com.osgateway.shared.model.SmsReportRequest
import com.osgateway.shared.model.TaskResultRequest

/**
 * SMS send/receive engine using SmsManager + report payloads for the API.
 */
class SmsEngine(private val context: Context) {

    fun canSend(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun canReceive(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun executeTask(task: GatewayTask): TaskResultRequest {
        val started = System.currentTimeMillis()
        val to = task.smsTo ?: task.phone
        val body = task.smsBody
        if (to.isNullOrBlank() || body.isNullOrBlank()) {
            return TaskResultRequest(
                taskId = task.id,
                status = "FAILED",
                errorMessage = "smsTo/smsBody manquants",
                durationMs = 0,
            )
        }
        return try {
            val sent = sendSms(to, body)
            JournalRepository.append("SMS ${if (sent) "envoyé" else "échec"} -> $to")
            TaskResultRequest(
                taskId = task.id,
                status = if (sent) "SUCCESS" else "FAILED",
                ussdResponse = body,
                durationMs = System.currentTimeMillis() - started,
                errorMessage = if (sent) null else "Échec envoi SMS",
            )
        } catch (e: Exception) {
            TaskResultRequest(
                taskId = task.id,
                status = "FAILED",
                errorMessage = e.message,
                durationMs = System.currentTimeMillis() - started,
            )
        }
    }

    fun sendSms(address: String, body: String): Boolean {
        if (!canSend()) {
            JournalRepository.append("SEND_SMS non accordé")
            return false
        }
        val smsManager = getSmsManager()
        val parts = smsManager.divideMessage(body)
        return try {
            // Do not wait for SENT broadcast — many OEMs never deliver it and left jobs stuck in SENDING.
            if (parts.size == 1) {
                smsManager.sendTextMessage(address, null, body, null, null)
            } else {
                @Suppress("UNCHECKED_CAST")
                smsManager.sendMultipartTextMessage(address, null, parts, null, null)
            }
            true
        } catch (e: Exception) {
            JournalRepository.append("SMS échec SmsManager: ${e.message}")
            false
        }
    }

    fun buildInboundReport(address: String, body: String, timestamp: Long): SmsReportRequest =
        SmsReportRequest(
            direction = "INBOUND",
            address = address,
            body = body,
            timestamp = timestamp,
            status = "RECEIVED",
        )

    fun buildOutboundReport(address: String, body: String, taskId: String?, ok: Boolean): SmsReportRequest =
        SmsReportRequest(
            direction = "OUTBOUND",
            address = address,
            body = body,
            timestamp = System.currentTimeMillis(),
            taskId = taskId,
            status = if (ok) "SENT" else "FAILED",
        )

    @Suppress("DEPRECATION")
    private fun getSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else {
            SmsManager.getDefault()
        }
    }
}
