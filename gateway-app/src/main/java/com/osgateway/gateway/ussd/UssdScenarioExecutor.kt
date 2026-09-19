package com.osgateway.gateway.ussd

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.shared.model.GatewayTask
import com.osgateway.shared.model.TaskResultRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes server-provided USSD scenarios (COMPOSE / READ / REPLY / WAIT / VALIDATE / EXTRACT /
 * WAIT_SMS = confirmation SMS gateway, source de vérité pour RETRAIT Orange Money).
 */
class UssdScenarioExecutor(
    private val context: Context,
    private val controller: UssdSessionController = UssdSessionController.instance,
) {

    suspend fun run(task: GatewayTask): TaskResultRequest = withContext(Dispatchers.Main.immediate) {
        val started = System.currentTimeMillis()
        JournalRepository.append("USSD task ${task.id} démarrée (opérateur=${task.operator})")

        if (!UssdAccessibilityService.isEnabled()) {
            JournalRepository.append("WARNING: AccessibilityService inactif — réponses auto limitées")
        }

        val result = controller.execute(task) { code ->
            dialUssd(code)
        }

        val duration = System.currentTimeMillis() - started
        val status = when {
            result.success -> "SUCCESS"
            result.waitingForSms -> "WAITING_SMS_CONFIRMATION"
            result.timedOut -> "TIMEOUT"
            else -> "FAILED"
        }
        JournalRepository.append("USSD task ${task.id} -> $status (${duration}ms)")

        TaskResultRequest(
            taskId = task.id,
            status = status,
            ussdResponse = result.ussdResponse,
            extracted = result.extracted,
            durationMs = duration,
            errorMessage = result.errorMessage,
            screenshotBase64 = result.screenshotBase64,
            stepLogs = result.stepLogs,
        )
    }

    private fun dialUssd(rawCode: String): Boolean {
        val code = normalizeUssd(rawCode)
        return try {
            // Prefer ACTION_CALL for encoded USSD; falls back to dialer if permission missing.
            val encoded = Uri.encode(code)
            val uri = Uri.parse("tel:$encoded")
            val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val canCall = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CALL_PHONE,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (canCall) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val telecom = context.getSystemService(TelecomManager::class.java)
                    // placeCall requires CALL_PHONE; some devices still need Intent
                    runCatching {
                        context.startActivity(callIntent)
                    }.onFailure {
                        telecom?.placeCall(uri, null)
                    }
                } else {
                    context.startActivity(callIntent)
                }
            } else {
                context.startActivity(dialIntent)
            }
            JournalRepository.append("USSD composé (code masqué)")
            true
        } catch (e: Exception) {
            JournalRepository.append("Échec composition USSD: ${e.message}")
            false
        }
    }

    private fun normalizeUssd(code: String): String {
        var c = code.trim()
        if (!c.startsWith("*") && !c.startsWith("#")) {
            // leave as-is for shortcodes
        }
        if (!c.endsWith("#") && c.contains("*")) {
            c = "$c#"
        }
        return c
    }

    fun networkOperatorName(): String? {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
        return tm.networkOperatorName
    }
}
