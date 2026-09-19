package com.osgateway.gateway.ussd

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Best-effort parser for USSD / telephony dialog window content.
 * Modern Android limits automation; this extracts visible text and actionable nodes.
 */
object UssdWindowParser {

    private val ussdHints = listOf(
        "ussd",
        "ussd message",
        "réponse",
        "reponse",
        "reply",
        "envoyer",
        "send",
        "annuler",
        "cancel",
        "ok",
        "menu",
        "vérifier",
        "verifier",
        "numéro",
        "numero",
        "confirmer",
        "attention",
        "solde",
        "transaction",
        "client",
        "montant",
        "message",
        "orange",
        "retrait",
        "effectué",
        "effectue",
        "merci",
        "secret",
    )

    data class ParsedWindow(
        val fullText: String,
        val lines: List<String>,
        val editableNodes: List<AccessibilityNodeInfo>,
        val clickableLabels: Map<String, AccessibilityNodeInfo>,
        val looksLikeUssd: Boolean,
    )

    fun parse(root: AccessibilityNodeInfo?): ParsedWindow {
        if (root == null) {
            return ParsedWindow("", emptyList(), emptyList(), emptyMap(), false)
        }
        val texts = mutableListOf<String>()
        val editables = mutableListOf<AccessibilityNodeInfo>()
        val clickables = linkedMapOf<String, AccessibilityNodeInfo>()
        walk(root, texts, editables, clickables)
        val full = texts.joinToString("\n").trim()
        val lower = full.lowercase()
        val hasSend = clickables.keys.any {
            it.contains("send", true) || it.contains("envoyer", true) || it.equals("ok", true)
        }
        val looksLikeGatewayJournal = lower.contains("effacer le journal")
            || (lower.contains("journal") && (lower.contains("événement") || lower.contains("evenement")))
            || lower.contains("poll tâches")
            || lower.contains("poll taches")
            || lower.contains("heartbeat")
            || lower.contains("boîte à outils")
            || lower.contains("boite a outils")
        val looksLike = !looksLikeGatewayJournal && (
            ussdHints.any { lower.contains(it) } ||
                (editables.isNotEmpty() && hasSend)
            )
        return ParsedWindow(full, texts, editables, clickables, looksLike)
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        texts: MutableList<String>,
        editables: MutableList<AccessibilityNodeInfo>,
        clickables: MutableMap<String, AccessibilityNodeInfo>,
    ) {
        val text = sequenceOf(node.text, node.contentDescription)
            .mapNotNull { it?.toString()?.trim() }
            .firstOrNull { it.isNotEmpty() }

        if (!text.isNullOrBlank()) {
            texts += text
        }
        if (node.isEditable) {
            editables += AccessibilityNodeInfo.obtain(node)
        }
        if (node.isClickable && !text.isNullOrBlank()) {
            clickables[text] = AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, texts, editables, clickables)
            child.recycle()
        }
    }

    fun findClickable(parsed: ParsedWindow, label: String?): AccessibilityNodeInfo? {
        if (label.isNullOrBlank()) return null
        val target = label.trim()
        parsed.clickableLabels.entries.firstOrNull { (k, _) ->
            k.equals(target, ignoreCase = true)
        }?.value?.let { return it }
        return parsed.clickableLabels.entries.firstOrNull { (k, _) ->
            k.contains(target, ignoreCase = true)
        }?.value
    }

    fun matchesExpected(windowText: String, pattern: String?): Boolean {
        if (pattern.isNullOrBlank()) return true
        return try {
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(windowText)
        } catch (_: Exception) {
            windowText.contains(pattern, ignoreCase = true)
        }
    }
}
