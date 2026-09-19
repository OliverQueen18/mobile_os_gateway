package com.osgateway.gateway.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OrangeMoneySmsParserTest {

    private val retraitCtx = OrangeMoneySmsParser.CorrelationContext(
        amount = 1000.0,
        customerPhone = "76396922",
        operationType = "RETRAIT",
    )

    @Before
    fun setUp() {
        OrangeMoneySmsParser.resetProcessedForTests()
    }

    @Test
    fun retrait_success() {
        val raw = """
            Le retrait de 1000 FCFA sur le 76396922 a ete effectue.
            Commission: 0 FCFA. Nouveau solde: 3501 FCFA.
            ID: CO260823.1704.B48859. OFM MALI
        """.trimIndent()
        val parsed = OrangeMoneySmsParser.parse(raw)
        assertEquals(OrangeMoneySmsParser.OutcomeKind.SUCCESS, parsed.kind)
        assertEquals("RETRAIT", parsed.type)
        assertEquals(1000L, parsed.amount)
        assertEquals("76396922", parsed.customerPhone)
        assertEquals("CO260823.1704.B48859", parsed.orangeTransactionId)
        assertTrue(OrangeMoneySmsParser.correlates(parsed, retraitCtx))
    }

    @Test
    fun retrait_success_accents() {
        val parsed = OrangeMoneySmsParser.parse("Le retrait de 1000 FCFA sur le 76396922 a été effectué.")
        assertEquals(OrangeMoneySmsParser.OutcomeKind.SUCCESS, parsed.kind)
        assertEquals("RETRAIT", parsed.type)
        assertTrue(OrangeMoneySmsParser.correlates(parsed, retraitCtx))
    }

    @Test
    fun retrait_failure_insufficient() {
        val parsed = OrangeMoneySmsParser.parse("Retrait impossible. Solde insuffisant.")
        assertEquals(OrangeMoneySmsParser.OutcomeKind.FAILED, parsed.kind)
        assertTrue(OrangeMoneySmsParser.correlates(parsed, retraitCtx))
    }

    @Test
    fun retrait_wrong_amount_not_correlated() {
        val parsed = OrangeMoneySmsParser.parse("Le retrait de 500 FCFA sur le 76396922 a ete effectue.")
        assertEquals(OrangeMoneySmsParser.OutcomeKind.SUCCESS, parsed.kind)
        assertFalse(OrangeMoneySmsParser.correlates(parsed, retraitCtx))
    }

    @Test
    fun retrait_wrong_phone_not_correlated() {
        val parsed = OrangeMoneySmsParser.parse("Le retrait de 1000 FCFA sur le 70000000 a ete effectue.")
        assertFalse(OrangeMoneySmsParser.correlates(parsed, retraitCtx))
    }

    @Test
    fun unrelated_ignored() {
        val parsed = OrangeMoneySmsParser.parse("Bienvenue sur Orange Money...")
        assertEquals(OrangeMoneySmsParser.OutcomeKind.IGNORED, parsed.kind)
        assertFalse(OrangeMoneySmsParser.correlates(parsed, retraitCtx))
    }

    @Test
    fun depot_success_as_transfert_sms() {
        val raw = "Le transfert de 200 FCFA au 76396922 a ete effectue. ID: CO260903.1700.A12345. OFM MALI"
        val parsed = OrangeMoneySmsParser.parse(raw)
        assertEquals(OrangeMoneySmsParser.OutcomeKind.SUCCESS, parsed.kind)
        assertEquals("TRANSFERT", parsed.type)
        assertEquals(200L, parsed.amount)
        val depotCtx = OrangeMoneySmsParser.CorrelationContext(200.0, "76396922", "DEPOT")
        assertTrue(OrangeMoneySmsParser.correlates(parsed, depotCtx))
        assertFalse(OrangeMoneySmsParser.correlates(parsed, retraitCtx))
    }

    @Test
    fun depot_cancelled() {
        val raw = "Le transfert de 100 FCFA du 76396922 avec l'ID de tranfert CO260828.1631.A55515 a ete annule car il n'a pas ete confirme dans les delais"
        val parsed = OrangeMoneySmsParser.parse(raw)
        assertEquals(OrangeMoneySmsParser.OutcomeKind.FAILED, parsed.kind)
        assertEquals(100L, parsed.amount)
        assertEquals("76396922", parsed.customerPhone)
        assertTrue(
            OrangeMoneySmsParser.correlates(
                parsed,
                OrangeMoneySmsParser.CorrelationContext(100.0, "76396922", "DEPOT"),
            ),
        )
    }

    @Test
    fun paiement_success_no_phone_required() {
        val raw = "Paiement de 5000 FCFA effectue chez Marchand XYZ. ID: CO260903.1200.P99999"
        val parsed = OrangeMoneySmsParser.parse(raw)
        assertEquals(OrangeMoneySmsParser.OutcomeKind.SUCCESS, parsed.kind)
        assertEquals("PAIEMENT", parsed.type)
        assertEquals(5000L, parsed.amount)
        assertTrue(
            OrangeMoneySmsParser.correlates(
                parsed,
                OrangeMoneySmsParser.CorrelationContext(5000.0, null, "PAIEMENT"),
            ),
        )
    }

    @Test
    fun achat_credit_success() {
        val raw = "Achat credit de 1000 FCFA effectue pour le 76396922. Succes."
        val parsed = OrangeMoneySmsParser.parse(raw)
        assertEquals(OrangeMoneySmsParser.OutcomeKind.SUCCESS, parsed.kind)
        assertEquals("ACHAT_CREDIT", parsed.type)
        assertEquals(1000L, parsed.amount)
        assertTrue(
            OrangeMoneySmsParser.correlates(
                parsed,
                OrangeMoneySmsParser.CorrelationContext(1000.0, "76396922", "ACHAT_CREDIT"),
            ),
        )
    }

    @Test
    fun achat_uv_success() {
        val raw = "Achat UV de 50000 FCFA a ete effectue. Nouveau float disponible."
        val parsed = OrangeMoneySmsParser.parse(raw)
        assertEquals(OrangeMoneySmsParser.OutcomeKind.SUCCESS, parsed.kind)
        assertEquals("ACHAT_UV", parsed.type)
        assertEquals(50000L, parsed.amount)
        assertTrue(
            OrangeMoneySmsParser.correlates(
                parsed,
                OrangeMoneySmsParser.CorrelationContext(50000.0, null, "ACHAT_UV"),
            ),
        )
    }

    @Test
    fun solde_sms() {
        val raw = """
            Le solde de votre Principal :125000 FCFA
            Bonus UV :2 FCFA
        """.trimIndent()
        val parsed = OrangeMoneySmsParser.parse(raw)
        assertEquals(OrangeMoneySmsParser.OutcomeKind.SUCCESS, parsed.kind)
        assertEquals("SOLDE", parsed.type)
        assertEquals(125000L, parsed.amount)
        assertEquals(125000L, OrangeMoneySmsParser.parseBalanceFromSms(raw, "ORANGE"))
    }

    @Test
    fun orange_id_extracted() {
        val parsed = OrangeMoneySmsParser.parse(
            "Le retrait de 1000 FCFA sur le 76396922 a ete effectue. ID: CO260823.1704.B48859. OFM MALI",
        )
        assertNotNull(parsed.orangeTransactionId)
        assertEquals("CO260823.1704.B48859", parsed.orangeTransactionId)
    }

    @Test
    fun duplicate_key_idempotent() {
        val raw = "Le retrait de 1000 FCFA sur le 76396922 a ete effectue. ID: CO260823.1704.B48859"
        val key = OrangeMoneySmsParser.duplicateKey("Orange", raw, 123L)
        assertTrue(OrangeMoneySmsParser.markProcessed(key))
        assertFalse(OrangeMoneySmsParser.markProcessed(key))
    }

    @Test
    fun normalize_strips_accents() {
        assertEquals("ete effectue succes", SmsNormalizer.normalize("Été effectué\nSuccès"))
    }

    @Test
    fun operation_aliases_depot_transfert() {
        assertTrue(OrangeMoneySmsParser.operationTypeMatches("TRANSFERT", "DEPOT"))
        assertTrue(OrangeMoneySmsParser.operationTypeMatches("DEPOT", "TRANSFERT"))
        assertFalse(OrangeMoneySmsParser.operationTypeMatches("RETRAIT", "DEPOT"))
    }
}
