package com.osgateway.gateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import com.osgateway.gateway.data.JournalRepository

/**
 * Anciennement : SMS tardif = confirmation TX.
 * Mode actuel : confirmation par solde uniquement — les SMS de confirmation sont ignorés.
 */
object LateSmsConfirmationReporter {

    @Suppress("UNUSED_PARAMETER")
    fun onInboundSms(
        receiver: BroadcastReceiver,
        context: Context,
        address: String,
        body: String,
        timestamp: Long,
    ) {
        PendingSmsConfirmationRegistry.tryMatch(address, body, timestamp) ?: return
        JournalRepository.append("[SMS] Confirmation SMS ignorée (mode solde uniquement)")
    }
}
