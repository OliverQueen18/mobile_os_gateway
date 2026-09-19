package com.osgateway.gateway.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.osgateway.gateway.data.JournalRepository

/**
 * Réception SMS_RECEIVED — publie vers [InboundSmsHub] pour WAIT_SMS,
 * puis tente une corrélation tardive ([LateSmsConfirmationReporter]).
 */
class SmsReceivedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        messages.groupBy { it.originatingAddress.orEmpty() }.forEach { (address, parts) ->
            val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
            val ts = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
            InboundSmsHub.publish(address, body, ts)
            JournalRepository.append("SMS reçu de $address (${body.length} car.)")
            LateSmsConfirmationReporter.onInboundSms(this, context, address, body, ts)
        }
    }
}
