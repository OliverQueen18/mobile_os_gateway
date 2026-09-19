package com.osgateway.shared.util

/**
 * Libellés français pour les statuts de transaction (apps mobiles).
 */
object TransactionStatusFr {
    fun label(status: String?): String {
        if (status.isNullOrBlank()) return "—"
        return when (status.trim().uppercase()) {
            "PENDING" -> "En attente"
            "QUEUED" -> "En file"
            "ASSIGNED" -> "Assignée"
            "PROCESSING" -> "En cours"
            "WAITING_SMS_CONFIRMATION" -> "Attente SMS"
            "SUCCESS" -> "Succès"
            "FAILED" -> "Échec"
            "CANCELLED", "CANCELED" -> "Annulée"
            "TIMEOUT" -> "Expirée"
            else -> status
        }
    }

    /** Retire les mentions de commission d'un message (notifications / push). */
    fun stripCommissionMentions(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()
        return text
            .lines()
            .filterNot { line ->
                val lower = line.lowercase()
                lower.contains("commission") || lower.contains("part distributeur") || lower.contains("part admin")
            }
            .joinToString("\n")
            .replace(Regex("(?i)[,·|]\\s*commission[^,·|\\n]*"), "")
            .replace(Regex("(?i)\\s{2,}"), " ")
            .trim()
    }
}
