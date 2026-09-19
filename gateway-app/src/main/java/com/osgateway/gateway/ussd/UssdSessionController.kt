package com.osgateway.gateway.ussd

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.osgateway.gateway.data.JournalRepository
import com.osgateway.gateway.sms.InboundSmsHub
import com.osgateway.gateway.sms.OrangeMoneySmsParser
import com.osgateway.gateway.sms.PendingSmsConfirmationRegistry
import com.osgateway.gateway.sms.SmsNormalizer
import com.osgateway.shared.model.GatewayTask
import com.osgateway.shared.model.StepLog
import com.osgateway.shared.model.UssdStep
import com.osgateway.shared.model.UssdStepAction
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

/**
 * Coordinates USSD scenario execution between the AccessibilityService and the task runner.
 * Steps: COMPOSE, READ, REPLY, WAIT, CONTINUE, VALIDATE, EXTRACT, WAIT_SMS
 * (WAIT_SMS = confirmation SMS gateway selon motifs/parser du template — source de vérité finale).
 */
class UssdSessionController {

    data class SessionState(
        val active: Boolean = false,
        val taskId: String? = null,
        val currentStep: Int = 0,
        val lastWindowText: String = "",
        val message: String = "Idle",
    )

    data class ExecutionResult(
        val success: Boolean,
        val ussdResponse: String,
        val extracted: Map<String, String>,
        val stepLogs: List<StepLog>,
        val errorMessage: String? = null,
        val screenshotBase64: String? = null,
        /** Timeout SMS confirmation (≠ FAILED explicite). */
        val timedOut: Boolean = false,
        /**
         * Soft-wait : USSD terminé, SMS pas encore reçu — gateway libéré,
         * confirmation possible en retard via [PendingSmsConfirmationRegistry].
         */
        val waitingForSms: Boolean = false,
    )

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val latestWindow = AtomicReference(UssdWindowParser.ParsedWindow("", emptyList(), emptyList(), emptyMap(), false))
    private val windowSignal = AtomicReference<CompletableDeferred<UssdWindowParser.ParsedWindow>?>(null)

    @Volatile
    var performGlobalAction: ((Int) -> Boolean)? = null

    @Volatile
    var screenshotCapture: ScreenshotCapture = MediaProjectionScreenshotStub()

    fun onWindowUpdated(parsed: UssdWindowParser.ParsedWindow) {
        latestWindow.set(parsed)
        _state.value = _state.value.copy(lastWindowText = parsed.fullText)
        windowSignal.get()?.complete(parsed)
        _events.tryEmit("WINDOW: ${parsed.fullText.take(200)}")
    }

    suspend fun execute(task: GatewayTask, composeDialer: suspend (String) -> Boolean): ExecutionResult =
        execute(task, composeDialer, nested = false)

    private suspend fun execute(
        task: GatewayTask,
        composeDialer: suspend (String) -> Boolean,
        nested: Boolean,
    ): ExecutionResult {
        val logs = mutableListOf<StepLog>()
        val extracted = linkedMapOf<String, String>()
        val variables = task.variables.toMutableMap().apply {
            // Numéro national sans indicatif pour {{phone}} dans les codes USSD
            val rawPhone = task.phone ?: this["phone"]
            rawPhone?.let { put("phone", Companion.toNationalDigits(it)) }
            // Jamais "100.0" : le composeur USSD strippe le point → "1000"
            task.amount?.let { put("amount", formatAmountForUssd(it)) }
                ?: this["amount"]?.let { put("amount", sanitizeAmountString(it)) }
            task.pin?.let { put("pin", it) }
        }
        _state.value = SessionState(true, task.id, 0, "", "Exécution ${task.id}")
        val steps = task.steps.sortedBy { it.order }
        val sessionStartMs = System.currentTimeMillis()
        if (steps.isEmpty() && !task.ussdCode.isNullOrBlank()) {
            return executeLegacyCompose(task, composeDialer, variables, logs)
        }

        var lastText = ""
        try {
            JournalRepository.append("[USSD] Transaction ${task.id} started")
            for ((index, step) in steps.withIndex()) {
                _state.value = _state.value.copy(currentStep = index, message = "${step.action} #${step.order}")
                val resolved = resolveTemplates(step.value, variables)
                val action = runCatching { UssdStepAction.valueOf(step.action.uppercase()) }
                    .getOrElse {
                        logs += StepLog(step.order, step.action, false, "Action inconnue")
                        return fail(lastText, extracted, logs, "Action inconnue: ${step.action}")
                    }

                when (action) {
                    UssdStepAction.COMPOSE -> {
                        val code = resolved ?: task.ussdCode
                        if (code.isNullOrBlank()) {
                            logs += StepLog(step.order, action.name, false, "Code USSD manquant")
                            return fail(lastText, extracted, logs, "Code USSD manquant")
                        }
                        val ok = composeDialer(code)
                        // Ne jamais logger le PIN : masquer digits après dernier *
                        logs += StepLog(step.order, action.name, ok, maskPinInUssd(code))
                        if (!ok) return fail(lastText, extracted, logs, "Échec composition USSD")
                        JournalRepository.append("[USSD] Code generated successfully")
                        delay(1_500)
                    }

                    UssdStepAction.READ, UssdStepAction.WAIT -> {
                        val timeout = stepTimeout(step.timeoutMs, 20_000L)
                        val window = awaitWindow(timeout) { parsed ->
                            parsed.looksLikeUssd &&
                                parsed.fullText.isNotBlank() &&
                                UssdWindowParser.matchesExpected(
                                    parsed.fullText,
                                    resolveTemplates(step.expectedPattern, variables),
                                )
                        }
                        if (window == null) {
                            logs += StepLog(step.order, action.name, false, "Timeout lecture (${timeout}ms)", lastText)
                            return fail(lastText, extracted, logs, "Timeout lecture USSD", captureShot())
                        }
                        lastText = window.fullText
                        logs += StepLog(step.order, action.name, true, "OK", window.fullText)
                    }

                    UssdStepAction.CONTINUE -> {
                        // Ferme le dialogue : OK ou Annuler.
                        // Soft-skip uniquement s'il reste un WAIT_SMS (ancien flux SMS).
                        // Sinon (confirmation par solde) : OK obligatoire, sinon FAILED.
                        val laterWaitsSms = steps.drop(index + 1).any {
                            it.action.equals("WAIT_SMS", ignoreCase = true)
                        }
                        JournalRepository.append("[USSD] Waiting customer confirmation / closing dialog")
                        // Orange est lent : honorer wait_millis jusqu'à 45s
                        val timeout = stepTimeout(step.timeoutMs, 25_000L).coerceAtMost(45_000L)
                        val preferred = step.clickLabel?.takeIf { it.isNotBlank() }
                            ?: resolved?.takeIf { it.isNotBlank() }
                        val pattern = resolveTemplates(step.expectedPattern, variables)
                        val window = awaitWindow(timeout) { parsed ->
                            if (parsed.fullText.isBlank()) return@awaitWindow false
                            if (!hasDismissButton(parsed, preferred)) return@awaitWindow false
                            when {
                                pattern.isNullOrBlank() -> parsed.looksLikeUssd
                                UssdWindowParser.matchesExpected(parsed.fullText, pattern) -> true
                                parsed.looksLikeUssd -> true
                                else -> false
                            }
                        }
                        if (window == null) {
                            val current = latestWindow.get()
                            if (hasDismissButton(current, preferred)) {
                                lastText = current.fullText
                                val insufficient = isInsufficientBalance(current.fullText)
                                val clicked = dismissDialog(
                                    current,
                                    preferred ?: if (insufficient) "Annuler" else null,
                                )
                                logs += StepLog(
                                    step.order,
                                    action.name,
                                    clicked || !insufficient,
                                    if (insufficient) "Solde insuffisant → Annuler" else "Fermeture (retry)",
                                    current.fullText,
                                )
                                if (insufficient) {
                                    return fail(
                                        lastText,
                                        extracted,
                                        logs,
                                        "Solde insuffisant (USSD)",
                                        captureShot(),
                                    )
                                }
                                if (!clicked && !laterWaitsSms) {
                                    return fail(
                                        lastText,
                                        extracted,
                                        logs,
                                        "Échec confirmation USSD (bouton OK non cliqué)",
                                        captureShot(),
                                    )
                                }
                                JournalRepository.append("[USSD] Dialog closed (retry)")
                                delay(300)
                            } else if (laterWaitsSms) {
                                logs += StepLog(
                                    step.order,
                                    action.name,
                                    true,
                                    "Dialogue final absent — poursuite (attente SMS)",
                                    lastText,
                                )
                                JournalRepository.append("[USSD] Dialog closed (absent) — waiting SMS")
                            } else {
                                logs += StepLog(
                                    step.order,
                                    action.name,
                                    false,
                                    "Dialogue USSD absent après composition",
                                    lastText,
                                )
                                return fail(
                                    lastText,
                                    extracted,
                                    logs,
                                    "Dialogue USSD absent — transaction non confirmée",
                                    captureShot(),
                                )
                            }
                        } else {
                            lastText = window.fullText
                            val insufficient = isInsufficientBalance(window.fullText)
                            var clicked = dismissDialog(window, preferred ?: if (insufficient) "Annuler" else null)
                            if (!clicked) {
                                delay(400)
                                clicked = dismissDialog(latestWindow.get(), preferred)
                            }
                            logs += StepLog(
                                step.order,
                                action.name,
                                clicked || !insufficient,
                                when {
                                    insufficient -> "Solde insuffisant → Annuler"
                                    else -> preferred ?: "OK|Annuler"
                                },
                                window.fullText,
                            )
                            if (insufficient) {
                                return fail(
                                    lastText,
                                    extracted,
                                    logs,
                                    "Solde insuffisant (USSD)",
                                    captureShot(),
                                )
                            }
                            if (!clicked && !laterWaitsSms) {
                                return fail(
                                    lastText,
                                    extracted,
                                    logs,
                                    "Échec confirmation USSD (bouton OK non cliqué)",
                                    captureShot(),
                                )
                            }
                            JournalRepository.append("[USSD] Dialog closed")
                            delay(800)
                        }
                    }

                    UssdStepAction.REPLY -> {
                        val reply = resolved ?: ""
                        val timeout = stepTimeout(step.timeoutMs, 25_000L)
                        // Exiger un champ éditable (sinon on saisissait sur le Journal / autre écran)
                        val ok = replyWithRetries(reply, step.clickLabel, timeout, logs, step.order)
                        if (!ok) {
                            lastText = latestWindow.get().fullText
                            // Écran solde insuffisant avec champ + Annuler (pas forcément une REPLY réussie)
                            if (isInsufficientBalance(lastText)) {
                                dismissDialog(latestWindow.get(), "Annuler")
                                return fail(lastText, extracted, logs, "Solde insuffisant (USSD)", captureShot())
                            }
                            return fail(lastText, extracted, logs, "Échec réponse USSD (pas de champ saisie)", captureShot())
                        }
                        lastText = latestWindow.get().fullText
                        if (isInsufficientBalance(lastText)) {
                            dismissDialog(latestWindow.get(), "Annuler")
                            logs += StepLog(step.order, "REPLY", false, "Solde insuffisant", lastText)
                            return fail(lastText, extracted, logs, "Solde insuffisant (USSD)", captureShot())
                        }
                        delay(600)
                    }

                    UssdStepAction.VALIDATE -> {
                        val window = awaitWindow(step.timeoutMs) {
                            it.fullText.isNotBlank() && it.looksLikeUssd
                        } ?: latestWindow.get().takeIf { it.looksLikeUssd }
                        if (window == null) {
                            return fail(lastText, extracted, logs, "Validation échouée (pas d'écran USSD)", captureShot())
                        }
                        lastText = window.fullText
                        val ok = UssdWindowParser.matchesExpected(
                            window.fullText,
                            resolveTemplates(step.expectedPattern, variables),
                        )
                        logs += StepLog(step.order, action.name, ok, step.expectedPattern, window.fullText)
                        if (!ok) return fail(lastText, extracted, logs, "Validation échouée", captureShot())
                    }

                    UssdStepAction.EXTRACT -> {
                        val window = awaitWindow(step.timeoutMs) {
                            it.fullText.isNotBlank() && it.looksLikeUssd
                        } ?: latestWindow.get().takeIf { it.looksLikeUssd }
                        if (window == null) {
                            return fail(lastText, extracted, logs, "Extraction échouée (pas d'écran USSD)", captureShot())
                        }
                        lastText = window.fullText
                        val name = step.variableName ?: "extract_${step.order}"
                        val balanceCheck = isBalanceCheckTask(task, variables)
                        if (balanceCheck && name == "mm_balance") {
                            val parsed = GatewayBalanceParser.parse(window.fullText, task.balancePatterns, task.operator)
                            if (parsed != null) {
                                extracted["mm_balance"] = parsed.principal.toString()
                                variables["mm_balance"] = parsed.principal.toString()
                                parsed.bonusUv?.let {
                                    extracted["bonus_uv"] = it.toString()
                                    variables["bonus_uv"] = it.toString()
                                }
                                extracted["balance_source"] = "USSD"
                                logs += StepLog(
                                    step.order,
                                    action.name,
                                    true,
                                    "Principal=${parsed.principal} bonusUv=${parsed.bonusUv}",
                                    window.fullText,
                                )
                                JournalRepository.append(
                                    "[BALANCE] Solde Principal (USSD): ${parsed.principal} XOF",
                                )
                                continue
                            }
                            logs += StepLog(
                                step.order,
                                action.name,
                                false,
                                "Principal non extrait écran — repli SMS",
                                window.fullText,
                            )
                            continue
                        }
                        val pattern = resolveTemplates(step.expectedPattern, variables)
                        val value = extractValue(window.fullText, pattern)
                        if (value != null) {
                            extracted[name] = value
                            variables[name] = value
                            logs += StepLog(step.order, action.name, true, "$name=$value", window.fullText)
                        } else {
                            logs += StepLog(step.order, action.name, false, "Non trouvé", window.fullText)
                            return fail(lastText, extracted, logs, "Extraction échouée: $name", captureShot())
                        }
                    }

                    UssdStepAction.RUN_TEMPLATE -> {
                        val nestedSteps = step.nestedSteps
                        if (nestedSteps.isEmpty()) {
                            return fail(
                                lastText,
                                extracted,
                                logs,
                                "Modèle ${resolved ?: "?"} introuvable ou vide",
                                captureShot(),
                            )
                        }
                        val targetType = (resolved ?: step.value).orEmpty().trim().uppercase()
                        val nestedVars = variables.toMutableMap()
                        if (targetType == "SOLDE") {
                            nestedVars["_balance_check"] = "true"
                            nestedVars["type"] = "SOLDE"
                        }
                        val nestedTask = task.copy(
                            id = "${task.id}-run-${step.order}",
                            steps = nestedSteps,
                            variables = nestedVars,
                            timeoutSeconds = 90,
                        )
                        JournalRepository.append("[USSD] RUN_TEMPLATE $targetType (${nestedSteps.size} étapes)")
                        val nestedResult = execute(nestedTask, composeDialer, nested = true)
                        logs += nestedResult.stepLogs
                        extracted.putAll(nestedResult.extracted)
                        nestedResult.extracted.forEach { (k, v) -> variables[k] = v }
                        val mm = nestedResult.extracted["mm_balance"]
                            ?: nestedResult.extracted["gateway_balance"]
                        val captureAs = step.variableName?.takeIf { it.isNotBlank() }
                        if (!mm.isNullOrBlank() && !captureAs.isNullOrBlank()) {
                            extracted[captureAs] = mm
                            variables[captureAs] = mm
                        }
                        if (!nestedResult.success && !nestedResult.waitingForSms) {
                            logs += StepLog(step.order, action.name, false, nestedResult.errorMessage, lastText)
                            return fail(
                                nestedResult.ussdResponse.ifBlank { lastText },
                                extracted,
                                logs,
                                nestedResult.errorMessage ?: "Échec modèle $targetType",
                                captureShot(),
                            )
                        }
                        if (!mm.isNullOrBlank() && captureAs == "gateway_balance_before") {
                            val amount = task.amount?.let { java.math.BigDecimal.valueOf(it.toLong()) }
                                ?: variables["amount"]?.toBigDecimalOrNull()
                            val before = GatewayBalanceReader.parseBalance(mm, task.balancePatterns)
                            val txType = variables["type"] ?: task.variables["type"]
                            if (before != null && amount != null &&
                                !GatewayBalanceReader.hasSufficientBalance(before, txType, amount)
                            ) {
                                return fail(
                                    lastText,
                                    extracted,
                                    logs,
                                    "Solde gateway insuffisant ($before XOF)",
                                    captureShot(),
                                )
                            }
                        }
                        lastText = nestedResult.ussdResponse.ifBlank { lastText }
                        logs += StepLog(
                            step.order,
                            action.name,
                            true,
                            "$targetType → ${captureAs ?: "mm_balance"}=${mm ?: "-"}",
                            lastText,
                        )
                    }

                    UssdStepAction.VERIFY_BALANCE -> {
                        val beforeRaw = extracted["gateway_balance_before"] ?: variables["gateway_balance_before"]
                        val afterRaw = extracted["gateway_balance_after"] ?: variables["gateway_balance_after"]
                        val before = GatewayBalanceReader.parseBalance(beforeRaw, task.balancePatterns)
                        val after = GatewayBalanceReader.parseBalance(afterRaw, task.balancePatterns)
                        val amount = task.amount?.let { java.math.BigDecimal.valueOf(it.toLong()) }
                            ?: variables["amount"]?.toBigDecimalOrNull()
                        val txType = variables["type"]?.takeIf { it.uppercase() != "SOLDE" }
                            ?: task.variables["type"]
                        if (before != null && after != null) {
                            val delta = after.subtract(before)
                            extracted["gateway_balance_delta"] = delta.toPlainString()
                            val matches = GatewayBalanceReader.balanceMatches(before, after, txType, amount)
                            if (matches != null) {
                                extracted["balance_confirmed"] = matches.toString()
                            }
                            logs += StepLog(
                                step.order,
                                action.name,
                                matches != false,
                                "before=$before after=$after delta=$delta match=$matches",
                                lastText,
                            )
                            if (matches == false) {
                                extracted["confirmation_source"] = "BALANCE"
                                return fail(
                                    lastText,
                                    extracted,
                                    logs,
                                    "Solde gateway incompatible avec la transaction (delta=$delta)",
                                    captureShot(),
                                )
                            }
                            if (matches == true) {
                                extracted["confirmation_source"] =
                                    extracted["confirmation_source"] ?: "BALANCE"
                            }
                        } else {
                            logs += StepLog(
                                step.order,
                                action.name,
                                true,
                                "Soldes incomplets before=$beforeRaw after=$afterRaw — skip",
                                lastText,
                            )
                        }
                    }

                    UssdStepAction.WAIT_SMS -> {
                        val since = sessionStartMs - 5_000L
                        val successPattern = resolveTemplates(step.expectedPattern, variables)
                        val failurePattern = resolveTemplates(step.value, variables)
                        val isBalanceCheck = isBalanceCheckTask(task, variables)

                        if (isBalanceCheck) {
                            val existing = extracted["mm_balance"] ?: variables["mm_balance"]
                            if (!existing.isNullOrBlank()) {
                                logs += StepLog(
                                    step.order,
                                    action.name,
                                    true,
                                    "SKIP_SMS (solde déjà lu USSD=$existing)",
                                    lastText,
                                )
                                JournalRepository.append("[BALANCE] SMS ignoré — solde déjà extrait: $existing XOF")
                                continue
                            }
                            val timeout = stepTimeout(step.timeoutMs, 60_000L).coerceIn(30_000L, 90_000L)
                            JournalRepository.append("[BALANCE] Attente SMS solde (${timeout}ms)")
                            logs += StepLog(step.order, action.name, true, "WAIT_BALANCE_SMS", lastText)
                            val sms = awaitBalanceSms(since, timeout, successPattern, task.operator, task.balancePatterns)
                            if (sms == null) {
                                return fail(
                                    lastText,
                                    extracted,
                                    logs,
                                    "SMS solde non reçu",
                                    captureShot(),
                                )
                            }
                            val parsed = GatewayBalanceParser.parse(sms.body, task.balancePatterns, task.operator)
                            if (parsed == null) {
                                return fail(
                                    sms.body,
                                    extracted,
                                    logs,
                                    "Solde Principal non extrait du SMS opérateur",
                                    captureShot(),
                                )
                            }
                            lastText = sms.body
                            extracted["mm_balance"] = parsed.principal.toString()
                            parsed.bonusUv?.let { extracted["bonus_uv"] = it.toString() }
                            extracted["sms_body"] = sms.body
                            extracted["sms_from"] = sms.address
                            extracted["balance_source"] = "SMS"
                            extracted["confirmation_source"] = "SMS"
                            step.variableName?.takeIf { it.isNotBlank() }?.let { name ->
                                extracted[name] = parsed.principal.toString()
                                variables[name] = parsed.principal.toString()
                            }
                            logs += StepLog(
                                step.order,
                                action.name,
                                true,
                                "Principal SMS=${parsed.principal} bonusUv=${parsed.bonusUv}",
                                sms.body.take(200),
                            )
                            JournalRepository.append(
                                "[BALANCE] Solde Principal (SMS): ${parsed.principal} XOF",
                            )
                            continue
                        }

                        // Source de vérité = SMS gateway (confirmationType=SMS via template).
                        // La fermeture USSD ne finalise PAS la transaction.
                        val timeout = stepTimeout(step.timeoutMs, SMS_CONFIRMATION_TIMEOUT_MS)
                            .coerceAtLeast(SMS_CONFIRMATION_TIMEOUT_MS)
                        val ctx = OrangeMoneySmsParser.CorrelationContext(
                            amount = task.amount ?: variables["amount"]?.toDoubleOrNull(),
                            customerPhone = task.phone ?: variables["phone"],
                            operationType = (variables["type"] ?: variables["transaction_type"] ?: "RETRAIT")
                                .uppercase(),
                        )
                        JournalRepository.append("[TRANSACTION] Waiting SMS confirmation (${timeout}ms)")
                        logs += StepLog(step.order, action.name, true, "WAITING_SMS_CONFIRMATION", lastText)

                        val outcome = awaitCorrelatedSms(since, timeout, ctx, successPattern, failurePattern)
                        when {
                            outcome == null -> {
                                // Soft-wait : ne pas TIMEOUT immédiatement — SMS peut arriver en retard
                                val lateDeadline = System.currentTimeMillis() + SMS_LATE_CONFIRMATION_WINDOW_MS
                                PendingSmsConfirmationRegistry.register(
                                    PendingSmsConfirmationRegistry.Pending(
                                        taskId = task.id,
                                        amount = ctx.amount,
                                        customerPhone = ctx.customerPhone,
                                        operationType = ctx.operationType ?: "RETRAIT",
                                        sinceMs = since,
                                        deadlineMs = lateDeadline,
                                        successPattern = successPattern,
                                        failurePattern = failurePattern,
                                    ),
                                )
                                logs += StepLog(
                                    step.order,
                                    action.name,
                                    true,
                                    "WAITING_SMS soft (${SMS_LATE_CONFIRMATION_WINDOW_MS}ms late window)",
                                    lastText,
                                )
                                JournalRepository.append(
                                    "[TRANSACTION] Transaction ${task.id} WAITING_SMS (soft) — late window ${SMS_LATE_CONFIRMATION_WINDOW_MS}ms",
                                )
                                return waitingSmsResult(
                                    scrubGatewayUiNoise(lastText),
                                    extracted,
                                    logs,
                                    "En attente SMS confirmation (retard opérateur possible)",
                                    captureShot(),
                                )
                            }
                            outcome.success -> {
                                PendingSmsConfirmationRegistry.remove(task.id)
                                val sms = outcome.sms
                                val parsed = outcome.parsed
                                lastText = sms.body
                                extracted["sms_body"] = sms.body
                                extracted["sms_from"] = sms.address
                                extracted["confirmation_sms"] = sms.body
                                extracted["confirmation_source"] = "SMS"
                                extracted["confirmation_received_at"] = sms.timestamp.toString()
                                parsed.amount?.let { extracted["parsed_amount"] = it.toString() }
                                parsed.customerPhone?.let { extracted["parsed_phone"] = it }
                                parsed.orangeTransactionId?.let {
                                    extracted["orange_transaction_id"] = it
                                }
                                step.variableName?.takeIf { it.isNotBlank() }?.let { name ->
                                    extracted[name] = sms.body
                                    variables[name] = sms.body
                                }
                                logs += StepLog(
                                    step.order,
                                    action.name,
                                    true,
                                    "SMS OK id=${parsed.orangeTransactionId ?: "-"}",
                                    sms.body.take(200),
                                )
                                JournalRepository.append(
                                    "[SMS] Withdrawal confirmation detected amount=${parsed.amount} phone=${parsed.customerPhone} id=${parsed.orangeTransactionId}",
                                )
                                JournalRepository.append("[TRANSACTION] Transaction ${task.id} confirmed SUCCESS")
                                val hasFollowUp = steps.drop(index + 1).any {
                                    val a = it.action.uppercase()
                                    a == "RUN_TEMPLATE" || a == "VERIFY_BALANCE"
                                }
                                if (!hasFollowUp) {
                                    return ExecutionResult(
                                        success = true,
                                        ussdResponse = sms.body,
                                        extracted = extracted,
                                        stepLogs = logs,
                                        screenshotBase64 = captureShot(),
                                    )
                                }
                                lastText = sms.body
                            }
                            else -> {
                                PendingSmsConfirmationRegistry.remove(task.id)
                                val sms = outcome.sms
                                lastText = sms.body
                                extracted["sms_body"] = sms.body
                                extracted["confirmation_sms"] = sms.body
                                extracted["confirmation_source"] = "SMS"
                                logs += StepLog(
                                    step.order,
                                    action.name,
                                    false,
                                    "SMS échec opérateur",
                                    sms.body.take(200),
                                )
                                JournalRepository.append("[TRANSACTION] Transaction ${task.id} confirmed FAILED")
                                return fail(
                                    lastText,
                                    extracted,
                                    logs,
                                    "SMS de confirmation: échec — ${sms.body.take(160)}",
                                    captureShot(),
                                )
                            }
                        }
                    }
                }
            }
            if (isBalanceCheckTask(task, variables) &&
                (extracted["mm_balance"].isNullOrBlank() && variables["mm_balance"].isNullOrBlank())
            ) {
                return fail(
                    scrubGatewayUiNoise(lastText),
                    extracted,
                    logs,
                    "Solde Principal non lu (écran USSD ni SMS)",
                    captureShot(),
                )
            }
            val responseText = scrubGatewayUiNoise(lastText)
            return ExecutionResult(true, responseText, extracted, logs, screenshotBase64 = captureShot())
        } catch (e: Exception) {
            return fail(scrubGatewayUiNoise(lastText), extracted, logs, e.message ?: "Erreur USSD", captureShot())
        } finally {
            if (!nested) {
                _state.value = SessionState(false, null, 0, lastText, "Terminé")
            }
        }
    }

    private suspend fun executeLegacyCompose(
        task: GatewayTask,
        composeDialer: suspend (String) -> Boolean,
        variables: MutableMap<String, String>,
        logs: MutableList<StepLog>,
    ): ExecutionResult {
        val code = resolveTemplates(task.ussdCode, variables)!!
        val ok = composeDialer(code)
        logs += StepLog(0, "COMPOSE", ok, code)
        if (!ok) return fail("", emptyMap(), logs, "Échec composition")
        val window = awaitWindow(30_000) { it.fullText.isNotBlank() }
        val text = window?.fullText.orEmpty()
        logs += StepLog(1, "READ", window != null, null, text)
        return if (window != null) {
            ExecutionResult(true, text, emptyMap(), logs)
        } else {
            fail(text, emptyMap(), logs, "Pas de réponse USSD", captureShot())
        }
    }

    private fun fail(
        text: String,
        extracted: Map<String, String>,
        logs: List<StepLog>,
        error: String,
        shot: String? = null,
    ) = ExecutionResult(false, scrubGatewayUiNoise(text), extracted, logs, error, shot, timedOut = false)

    private fun timeoutResult(
        text: String,
        extracted: Map<String, String>,
        logs: List<StepLog>,
        error: String,
        shot: String? = null,
    ) = ExecutionResult(false, scrubGatewayUiNoise(text), extracted, logs, error, shot, timedOut = true)

    private fun waitingSmsResult(
        text: String,
        extracted: Map<String, String>,
        logs: List<StepLog>,
        message: String,
        shot: String? = null,
    ) = ExecutionResult(
        success = false,
        ussdResponse = scrubGatewayUiNoise(text),
        extracted = extracted,
        stepLogs = logs,
        errorMessage = message,
        screenshotBase64 = shot,
        timedOut = false,
        waitingForSms = true,
    )

    /** Évite d'envoyer le journal UI de l'app comme ussdResponse / SMS distributeur. */
    private fun scrubGatewayUiNoise(text: String?): String {
        if (text.isNullOrBlank()) return ""
        val lower = text.lowercase()
        if (lower.contains("effacer le journal")
            || (lower.contains("journal") && (lower.contains("événement") || lower.contains("evenement")))
            || lower.contains("poll tâches")
            || lower.contains("poll taches")
            || lower.contains("heartbeat")
            || lower.contains("boîte à outils")
            || lower.contains("boite a outils")
        ) {
            return ""
        }
        return text
    }

    private fun captureShot(): String? = runCatching { screenshotCapture.captureBase64() }.getOrNull()

    private suspend fun awaitWindow(
        timeoutMs: Long,
        predicate: (UssdWindowParser.ParsedWindow) -> Boolean,
    ): UssdWindowParser.ParsedWindow? {
        val current = latestWindow.get()
        if (predicate(current)) return current
        val result = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val deferred = CompletableDeferred<UssdWindowParser.ParsedWindow>()
                windowSignal.set(deferred)
                val w = deferred.await()
                if (predicate(w)) return@withTimeoutOrNull w
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
        windowSignal.set(null)
        if (result != null) return result
        return latestWindow.get().takeIf(predicate)
    }

    private fun stepTimeout(configuredMs: Long, fallbackMs: Long): Long {
        val base = if (configuredMs <= 0L) fallbackMs else configuredMs
        // Les dialogues USSD sont lents : ne jamais attendre moins de 12s
        return maxOf(base, 12_000L)
    }

    private suspend fun replyWithRetries(
        reply: String,
        clickLabel: String?,
        timeoutMs: Long,
        logs: MutableList<StepLog>,
        stepOrder: Int,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var attempt = 0
        while (System.currentTimeMillis() < deadline) {
            attempt++
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(500L)
            val window = awaitWindow(remaining) {
                it.editableNodes.isNotEmpty() && it.looksLikeUssd
            } ?: awaitWindow(remaining) {
                it.editableNodes.isNotEmpty()
            }
            if (window == null) {
                logs += StepLog(stepOrder, "REPLY", false, "Timeout champ saisie (essai $attempt)", latestWindow.get().fullText)
                break
            }
            val ok = replyInWindow(window, reply, clickLabel)
            logs += StepLog(stepOrder, "REPLY", ok, "$reply (essai $attempt)", window.fullText)
            if (ok) return true
            delay(700)
        }
        return false
    }

    private fun replyInWindow(
        window: UssdWindowParser.ParsedWindow,
        reply: String,
        clickLabel: String?,
    ): Boolean {
        val editable = window.editableNodes.firstOrNull()
        if (editable != null) {
            runCatching {
                editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, reply)
            }
            val set = editable.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            val button = UssdWindowParser.findClickable(window, clickLabel)
                ?: UssdWindowParser.findClickable(window, "Envoyer")
                ?: UssdWindowParser.findClickable(window, "Send")
                ?: UssdWindowParser.findClickable(window, "OK")
            val clicked = button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            return set && (clicked || button == null)
        }
        val digit = UssdWindowParser.findClickable(window, reply)
        return digit?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun isInsufficientBalance(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return UssdWindowParser.matchesExpected(
            text,
            "insuffisant|solde insuffisant|montant correct|fonds insuffisants",
        )
    }

    private fun hasDismissButton(
        window: UssdWindowParser.ParsedWindow,
        preferred: String?,
    ): Boolean {
        if (!preferred.isNullOrBlank() && UssdWindowParser.findClickable(window, preferred) != null) {
            return true
        }
        return DISMISS_LABELS.any { UssdWindowParser.findClickable(window, it) != null }
    }

    private fun dismissDialog(
        window: UssdWindowParser.ParsedWindow,
        preferred: String?,
    ): Boolean {
        val labels = buildList {
            if (!preferred.isNullOrBlank()) add(preferred)
            addAll(DISMISS_LABELS)
        }.distinctBy { it.lowercase() }
        for (label in labels) {
            val node = UssdWindowParser.findClickable(window, label) ?: continue
            if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
        }
        return false
    }

    private fun extractValue(text: String, pattern: String?): String? {
        if (pattern.isNullOrBlank()) return text.lines().lastOrNull { it.isNotBlank() }
        return try {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(text) ?: return null
            match.groups[1]?.value ?: match.value
        } catch (_: Exception) {
            null
        }
    }

    private fun maskPinInUssd(code: String): String {
        // Masque le dernier segment numérique (souvent le PIN gateway) pour les logs
        return code.replace(Regex("""(\*\d+)(?=#?$)"""), "*****")
    }

    private data class SmsWaitOutcome(
        val sms: InboundSmsHub.InboundSms,
        val parsed: OrangeMoneySmsParser.ParsedSms,
        val success: Boolean,
    )

    private suspend fun awaitBalanceSms(
        sinceMs: Long,
        timeoutMs: Long,
        successPattern: String?,
        operator: String?,
        patterns: List<com.osgateway.shared.model.BalancePattern> = emptyList(),
    ): InboundSmsHub.InboundSms? {
        val seen = mutableSetOf<String>()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            for (sms in InboundSmsHub.snapshotSince(sinceMs)) {
                val k = OrangeMoneySmsParser.duplicateKey(sms.address, sms.body, sms.timestamp)
                if (!seen.add(k)) continue
                val balance = GatewayBalanceParser.parsePrincipal(sms.body, patterns, operator)
                val matchesPattern = successPattern.isNullOrBlank() ||
                    UssdWindowParser.matchesExpected(sms.body, successPattern)
                if (balance != null && matchesPattern) {
                    if (!OrangeMoneySmsParser.markProcessed(k)) continue
                    return sms
                }
            }
            delay(300)
        }
        return null
    }

    private fun isBalanceCheckTask(task: GatewayTask, variables: Map<String, String>): Boolean =
        variables["_balance_check"] == "true" ||
            variables["type"]?.uppercase() == "SOLDE" ||
            task.id.endsWith("-solde")

    private suspend fun awaitCorrelatedSms(
        sinceMs: Long,
        timeoutMs: Long,
        ctx: OrangeMoneySmsParser.CorrelationContext,
        successPattern: String?,
        failurePattern: String?,
    ): SmsWaitOutcome? {
        val seen = mutableSetOf<String>()
        fun key(sms: InboundSmsHub.InboundSms) =
            OrangeMoneySmsParser.duplicateKey(sms.address, sms.body, sms.timestamp)

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            for (sms in InboundSmsHub.snapshotSince(sinceMs)) {
                val k = key(sms)
                if (!seen.add(k)) continue

                val parsed = OrangeMoneySmsParser.parse(sms.body)
                if (parsed.kind == OrangeMoneySmsParser.OutcomeKind.IGNORED) {
                    val byPattern = classifyByTemplatePatterns(sms.body, successPattern, failurePattern)
                    when (byPattern) {
                        false -> {
                            if (!OrangeMoneySmsParser.markProcessed(k)) continue
                            return SmsWaitOutcome(
                                sms,
                                parsed.copy(kind = OrangeMoneySmsParser.OutcomeKind.FAILED),
                                success = false,
                            )
                        }
                        true -> {
                            // Succès via motif template : exiger montant+tél si extractibles
                            val amountMatch = Regex(
                                """(\d+(?:[.,]\d+)?)\s*(?:fcfa|f\s*cfa|xof)""",
                                RegexOption.IGNORE_CASE,
                            ).find(SmsNormalizer.normalize(sms.body))
                            val phoneMatch = Regex("""(\d{6,15})""").findAll(SmsNormalizer.normalize(sms.body))
                                .map { it.groupValues[1] }
                                .firstOrNull { it.length in 8..15 }
                            val amount = amountMatch?.groupValues?.getOrNull(1)
                                ?.replace(',', '.')?.toDoubleOrNull()?.let { Math.round(it) }
                            val enriched = parsed.copy(
                                kind = OrangeMoneySmsParser.OutcomeKind.SUCCESS,
                                type = OrangeMoneySmsParser.normalizeOpCode(ctx.operationType) ?: "DEPOT",
                                amount = amount,
                                customerPhone = phoneMatch,
                            )
                            if (!OrangeMoneySmsParser.correlates(enriched, ctx) &&
                                (amount != null || phoneMatch != null)
                            ) {
                                JournalRepository.append("[SMS] Template success ignored (no correlation)")
                                continue
                            }
                            if (!OrangeMoneySmsParser.markProcessed(k)) continue
                            return SmsWaitOutcome(sms, enriched, success = true)
                        }
                        null -> continue
                    }
                }

                if (!OrangeMoneySmsParser.correlates(parsed, ctx)) {
                    JournalRepository.append("[SMS] Ignored (no correlation) kind=${parsed.kind}")
                    continue
                }
                if (!OrangeMoneySmsParser.markProcessed(k)) {
                    JournalRepository.append("[SMS] Duplicate ignored")
                    continue
                }
                return SmsWaitOutcome(
                    sms,
                    parsed,
                    success = parsed.kind == OrangeMoneySmsParser.OutcomeKind.SUCCESS,
                )
            }
            delay(300)
        }
        return null
    }

    /**
     * Motifs template optionnels (success = expectedPattern, failure = expression).
     * Utilisés seulement si le parser structuré ignore le SMS.
     * Exige quand même corrélation montant/téléphone si extractibles.
     */
    private fun classifyByTemplatePatterns(
        body: String,
        successPattern: String?,
        failurePattern: String?,
    ): Boolean? {
        val failure = !failurePattern.isNullOrBlank() &&
            UssdWindowParser.matchesExpected(body, failurePattern)
        val success = !successPattern.isNullOrBlank() &&
            UssdWindowParser.matchesExpected(body, successPattern)
        return when {
            failure -> false
            success -> true
            else -> null
        }
    }

    private fun resolveTemplates(template: String?, variables: Map<String, String>): String? {
        if (template == null) return null
        var result = template
        variables.forEach { (k, v) ->
            result = result!!.replace("{{$k}}", v, ignoreCase = true)
        }
        return result
    }

    companion object {
        val instance = UssdSessionController()

        /** Délai confirmation SMS client Orange Money (~2 min) — attente bloquante. */
        const val SMS_CONFIRMATION_TIMEOUT_MS = 120_000L

        /**
         * Fenêtre soft après le délai bloquant : SMS encore acceptés (retard opérateur).
         * Aligné sur expire backend (~15 min au total depuis WAITING_SMS).
         */
        const val SMS_LATE_CONFIRMATION_WINDOW_MS = 13 * 60_000L

        /** Boutons de fermeture des dialogues USSD finaux (succès ou échec). */
        private val DISMISS_LABELS = listOf(
            "OK",
            "Ok",
            "Annuler",
            "Cancel",
            "Fermer",
            "Close",
            "Continuer",
            "Continue",
        )

        /**
         * Montant USSD en chiffres entiers uniquement (XOF).
         * Évite `100.0` → composeur → `1000`.
         */
        fun formatAmountForUssd(amount: Double): String {
            val rounded = kotlin.math.round(amount)
            return rounded.toLong().toString()
        }

        fun sanitizeAmountString(raw: String): String {
            val normalized = raw.trim().replace(',', '.')
            val asDouble = normalized.toDoubleOrNull()
            return if (asDouble != null) formatAmountForUssd(asDouble) else raw.filter { it.isDigit() }
        }

        /**
         * Retire l'indicatif pays pour la composition USSD (ex. +22370123456 → 70123456).
         */
        fun toNationalDigits(phone: String): String {
            var digits = phone.filter { it.isDigit() }
            if (digits.startsWith("00")) digits = digits.drop(2)
            for (dial in DIAL_CODES) {
                if (digits.startsWith(dial) && digits.length > dial.length) {
                    val national = digits.drop(dial.length)
                    if (national.length >= 6) return national
                }
            }
            return digits
        }

        private val DIAL_CODES = listOf(
            "971", "966", "351", "291", "269", "267", "265", "264", "263", "261",
            "260", "258", "257", "256", "255", "254", "253", "252", "251", "250",
            "249", "248", "245", "244", "243", "242", "241", "240", "239", "238",
            "237", "236", "235", "234", "233", "232", "231", "230", "229", "228",
            "227", "226", "225", "224", "223", "222", "221", "220", "218", "216",
            "213", "212", "211", "86", "91", "49", "44", "41", "39", "34", "33",
            "32", "27", "20", "1",
        )
    }
}
