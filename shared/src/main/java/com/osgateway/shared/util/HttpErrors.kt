package com.osgateway.shared.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException

/**
 * Traduit les erreurs HTTP / réseau en messages français simples pour l'UI mobile.
 */
object HttpErrors {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun userMessage(
        error: Throwable?,
        fallback: String = "Une erreur est survenue. Réessayez.",
    ): String {
        if (error == null) return fallback
        if (error is CancellationException) throw error
        return when (error) {
            is HttpException -> fromHttp(error)
            is UnknownHostException -> "Impossible de joindre le serveur. Vérifiez votre connexion."
            is SocketTimeoutException -> "Le serveur met trop de temps à répondre. Réessayez."
            is IOException -> "Pas de connexion internet. Vérifiez votre réseau."
            else -> translateText(error.message) ?: fallback
        }
    }

    /** Message API métier (corps success=false) → français facile. */
    fun fromApi(
        message: String?,
        fallback: String = "L'opération a échoué. Réessayez.",
    ): String = translateText(message) ?: fallback

    private fun fromHttp(error: HttpException): String {
        val bodyMsg = parseBodyMessage(error)
        if (!bodyMsg.isNullOrBlank()) {
            return translateText(bodyMsg) ?: bodyMsg
        }
        return when (error.code()) {
            400 -> "Les informations saisies sont incorrectes."
            401 -> "Session expirée. Veuillez vous reconnecter."
            403 -> "Vous n'avez pas l'autorisation pour cette action."
            404 -> "Élément introuvable."
            408 -> "La requête a expiré. Réessayez."
            409 -> "Conflit : cette action n'est plus possible."
            422 -> "Données invalides. Vérifiez le formulaire."
            429 -> "Trop de tentatives. Attendez un moment."
            in 500..599 -> "Le serveur rencontre un problème. Réessayez plus tard."
            else -> "Erreur réseau (${error.code()}). Réessayez."
        }
    }

    private fun parseBodyMessage(error: HttpException): String? {
        return try {
            val raw = error.response()?.errorBody()?.string()?.trim().orEmpty()
            if (raw.isEmpty()) return null
            val obj = json.parseToJsonElement(raw).jsonObject
            obj["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: obj["error"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: obj["detail"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun translateText(raw: String?): String? {
        val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val lower = text.lowercase()

        // Déjà en français simple → garder
        if (looksFrench(text) && !looksTechnical(lower)) return text

        knownExact[lower]?.let { return it }
        knownContains.forEach { (needle, fr) ->
            if (lower.contains(needle)) return fr
        }

        // Messages techniques Retrofit / OkHttp
        when {
            lower.startsWith("http 401") || lower.contains("unauthorized") ->
                return "Session expirée. Veuillez vous reconnecter."
            lower.startsWith("http 403") || lower.contains("forbidden") ->
                return "Vous n'avez pas l'autorisation pour cette action."
            lower.startsWith("http 404") || lower.contains("not found") ->
                return "Élément introuvable."
            lower.startsWith("http 500") || lower.contains("internal server") ->
                return "Le serveur rencontre un problème. Réessayez plus tard."
            lower.contains("failed to connect") || lower.contains("connection refused") ->
                return "Impossible de se connecter au serveur."
            lower.contains("timeout") || lower.contains("timed out") ->
                return "Le serveur met trop de temps à répondre. Réessayez."
            lower.contains("unable to resolve host") || lower.contains("no address associated") ->
                return "Impossible de joindre le serveur. Vérifiez votre connexion."
            lower.contains("cleartext") ->
                return "Connexion non sécurisée refusée. Vérifiez l'URL de l'API."
            lower.contains("ssl") || lower.contains("certificate") ->
                return "Problème de sécurité de connexion (certificat)."
            lower.contains("expected begin") || lower.contains("json") && lower.contains("malformed") ->
                return "Réponse serveur invalide."
            looksTechnical(lower) ->
                return "Une erreur technique est survenue. Réessayez."
        }
        return text
    }

    private fun looksFrench(text: String): Boolean {
        val frHints = listOf(
            "é", "è", "ê", "à", "ù", "ô", "ç", "œ",
            "erreur", "échec", "impossible", "veuillez", "réessay",
            "connexion", "serveur", "identifiant", "mot de passe",
            "opération", "transaction", "hors ligne", "requis",
        )
        val lower = text.lowercase()
        return frHints.any { lower.contains(it) }
    }

    private fun looksTechnical(lower: String): Boolean =
        lower.contains("exception") ||
            lower.contains("stack") ||
            lower.contains("nullpointer") ||
            lower.contains("at com.") ||
            lower.contains("retrofit") ||
            lower.contains("okhttp") ||
            lower.contains("status code") ||
            Regex("""\b[a-z]+(\.[a-z]+)+\b""").containsMatchIn(lower) &&
            (lower.contains("java.") || lower.contains("kotlin.") || lower.contains("android."))

    private val knownExact = mapOf(
        "bad credentials" to "Identifiant ou mot de passe incorrect.",
        "invalid credentials" to "Identifiant ou mot de passe incorrect.",
        "invalid username or password" to "Identifiant ou mot de passe incorrect.",
        "mot de passe actuel incorrect" to "Mot de passe actuel incorrect.",
        "current password" to "Mot de passe actuel incorrect.",
        "user not found" to "Compte introuvable.",
        "access denied" to "Accès refusé.",
        "forbidden" to "Vous n'avez pas l'autorisation pour cette action.",
        "unauthorized" to "Session expirée. Veuillez vous reconnecter.",
        "token expired" to "Session expirée. Veuillez vous reconnecter.",
        "invalid token" to "Session invalide. Reconnectez-vous.",
        "pin invalid" to "PIN incorrect.",
        "invalid pin" to "PIN incorrect.",
        "wrong pin" to "PIN incorrect.",
        "insufficient balance" to "Solde insuffisant.",
        "balance insufficient" to "Solde insuffisant.",
        "validation error" to "Vérifiez les informations saisies.",
        "validation failed" to "Vérifiez les informations saisies.",
        "duplicate" to "Cette information existe déjà.",
        "already exists" to "Cette information existe déjà.",
        "not found" to "Élément introuvable.",
        "internal error" to "Erreur serveur. Réessayez plus tard.",
        "service unavailable" to "Service temporairement indisponible.",
    )

    private val knownContains = listOf(
        "bad credentials" to "Identifiant ou mot de passe incorrect.",
        "invalid credentials" to "Identifiant ou mot de passe incorrect.",
        "username or password" to "Identifiant ou mot de passe incorrect.",
        "insufficient balance" to "Solde insuffisant.",
        "insufficient funds" to "Solde insuffisant.",
        "invalid pin" to "PIN incorrect.",
        "wrong pin" to "PIN incorrect.",
        "pin is required" to "Le PIN est obligatoire.",
        "current pin" to "PIN actuel incorrect.",
        "account inactive" to "Compte inactif.",
        "account locked" to "Compte verrouillé. Contactez le support.",
        "registration" to "Dossier d'inscription non validé.",
        "already exists" to "Cette information existe déjà.",
        "duplicate key" to "Cette information existe déjà.",
        "constraint" to "Données invalides ou déjà utilisées.",
        "gateway" to "Passerelle indisponible. Réessayez plus tard.",
        "timeout" to "Délai dépassé. Réessayez.",
    )
}

fun Throwable.userMessageFr(fallback: String = "Une erreur est survenue. Réessayez."): String =
    HttpErrors.userMessage(this, fallback)

fun String?.apiMessageFr(fallback: String = "L'opération a échoué. Réessayez."): String =
    HttpErrors.fromApi(this, fallback)
