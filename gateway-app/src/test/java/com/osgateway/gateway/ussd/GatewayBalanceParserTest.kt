package com.osgateway.gateway.ussd

import com.osgateway.shared.model.BalancePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayBalanceParserTest {

    private val orangeDialog = """
        Le solde de votre Principal :125000 FCFA
        Bonus UV :2 FCFA
    """.trimIndent()

    @Test
    fun parse_orange_principal_not_bonus_with_defaults() {
        val parsed = GatewayBalanceParser.parse(orangeDialog)
        assertEquals(125000L, parsed?.principal)
        assertEquals(2L, parsed?.bonusUv)
    }

    @Test
    fun parse_orange_principal_with_spaces() {
        assertEquals(
            12_345L,
            GatewayBalanceParser.parsePrincipal(
                "Le solde de votre Principal : 12 345 FCFA\nBonus UV :2 FCFA",
            ),
        )
    }

    @Test
    fun parse_uses_configured_patterns() {
        val patterns = listOf(
            BalancePattern(
                fieldType = "PRINCIPAL",
                regexPattern = """compte\s+xyz\s*:?\s*([0-9]+)""",
                priority = 1,
            ),
        )
        assertEquals(
            999L,
            GatewayBalanceParser.parsePrincipal("Compte XYZ :999 FCFA", patterns),
        )
    }

    @Test
    fun parse_ignores_unrelated_sms() {
        assertNull(
            GatewayBalanceParser.parsePrincipal(
                "Le retrait de 1000 FCFA sur le 76396922 a ete effectue.",
            ),
        )
    }
}
