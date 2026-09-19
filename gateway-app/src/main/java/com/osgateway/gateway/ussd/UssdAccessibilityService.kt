package com.osgateway.gateway.ussd

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.osgateway.gateway.data.JournalRepository

/**
 * AccessibilityService that detects USSD dialogs, reads content, and enables auto-reply
 * via [UssdSessionController]. Platform limits apply on modern Android — best-effort.
 */
class UssdAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }
        UssdSessionController.instance.performGlobalAction = { action ->
            performGlobalAction(action)
        }
        instance = this
        JournalRepository.append("UssdAccessibilityService connecté")
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            -> handleWindow()
        }
    }

    private fun handleWindow() {
        val root = rootInActiveWindow ?: return
        try {
            val parsed = UssdWindowParser.parse(root)
            if (parsed.fullText.isBlank()) return
            if (parsed.looksLikeUssd || UssdSessionController.instance.state.value.active) {
                UssdSessionController.instance.onWindowUpdated(parsed)
            }
        } catch (e: Exception) {
            Log.w(TAG, "parse failed", e)
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {
        JournalRepository.append("UssdAccessibilityService interrompu")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        JournalRepository.append("UssdAccessibilityService déconnecté")
        return super.onUnbind(intent)
    }

    companion object {
        private const val TAG = "UssdA11y"

        @Volatile
        var instance: UssdAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null
    }
}
