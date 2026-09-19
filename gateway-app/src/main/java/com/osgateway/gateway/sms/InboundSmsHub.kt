package com.osgateway.gateway.sms

/**
 * Bus in-process des SMS entrants pour les scénarios USSD en WAIT_SMS
 * et soft-wait (SMS opérateur en retard).
 */
object InboundSmsHub {

    data class InboundSms(
        val address: String,
        val body: String,
        val timestamp: Long,
    )

    private val lock = Any()
    private val recent = ArrayDeque<InboundSms>(96)

    fun publish(address: String, body: String, timestamp: Long = System.currentTimeMillis()) {
        val sms = InboundSms(address = address.orEmpty(), body = body, timestamp = timestamp)
        synchronized(lock) {
            recent.addLast(sms)
            while (recent.size > 96) {
                recent.removeFirst()
            }
        }
    }

    /** Snapshot des messages reçus à partir de [sinceMs] (inclus). */
    fun snapshotSince(sinceMs: Long): List<InboundSms> = synchronized(lock) {
        recent.filter { it.timestamp >= sinceMs }
    }

    fun clearForTests() {
        synchronized(lock) { recent.clear() }
    }
}
