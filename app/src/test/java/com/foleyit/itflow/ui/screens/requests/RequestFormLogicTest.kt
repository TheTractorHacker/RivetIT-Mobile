package com.foleyit.itflow.ui.screens.requests

import com.foleyit.itflow.data.model.CatalogField
import com.foleyit.itflow.data.model.FieldType
import com.foleyit.itflow.data.model.ShowIfRule
import org.junit.Assert.*
import org.junit.Test

class RequestFormLogicTest {
    private fun f(key: String, type: FieldType, required: Boolean = false, options: List<String> = emptyList(), rule: ShowIfRule? = null) =
        CatalogField(key, key, type, options, required, "", rule)

    @Test fun `required text and empty optional`() {
        val fields = listOf(f("a", FieldType.TEXT, required = true), f("b", FieldType.TEXT))
        assertEquals(mapOf("a" to FieldError.Required), RequestFormLogic.validate(fields, mapOf("a" to "  ")))
        assertTrue(RequestFormLogic.validate(fields, mapOf("a" to "x")).isEmpty())
    }

    @Test fun `required checkbox must be ticked`() {
        val fields = listOf(f("ok", FieldType.CHECKBOX, required = true))
        assertEquals(FieldError.MustBeTicked, RequestFormLogic.validate(fields, emptyMap())["ok"])
        assertEquals(FieldError.MustBeTicked, RequestFormLogic.validate(fields, mapOf("ok" to "0"))["ok"])
        assertTrue(RequestFormLogic.validate(fields, mapOf("ok" to "1")).isEmpty())
    }

    @Test fun `length limits count characters`() {
        val text = listOf(f("t", FieldType.TEXT), f("a", FieldType.TEXTAREA))
        assertTrue(RequestFormLogic.validate(text, mapOf("t" to "x".repeat(500), "a" to "y".repeat(5000))).isEmpty())
        val errs = RequestFormLogic.validate(text, mapOf("t" to "x".repeat(501), "a" to "y".repeat(5001)))
        assertEquals(FieldError.TooLong(500), errs["t"])
        assertEquals(FieldError.TooLong(5000), errs["a"])
    }

    @Test fun `numbers`() {
        val n = listOf(f("n", FieldType.NUMBER))
        listOf("12", "-3.5", ".5", "1e3", "0", "+7").forEach {
            assertTrue("'$it' should be a number", RequestFormLogic.validate(n, mapOf("n" to it)).isEmpty())
        }
        listOf("abc", "1,5", "0x10", "5d", "NaN", "Infinity", "1e13", "--1", "1.2.3").forEach {
            assertEquals("'$it' should be rejected", FieldError.NotANumber, RequestFormLogic.validate(n, mapOf("n" to it))["n"])
        }
    }

    @Test fun `dates must be strict iso`() {
        val d = listOf(f("d", FieldType.DATE))
        assertTrue(RequestFormLogic.validate(d, mapOf("d" to "2026-02-28")).isEmpty())
        assertTrue(RequestFormLogic.validate(d, mapOf("d" to "2028-02-29")).isEmpty())
        listOf("2026-02-30", "2026-13-01", "26-01-01", "2026/01/01", "2026-1-1", "tomorrow").forEach {
            assertEquals("'$it'", FieldError.BadDate, RequestFormLogic.validate(d, mapOf("d" to it))["d"])
        }
    }

    @Test fun `select must be one of the options`() {
        val s = listOf(f("s", FieldType.SELECT, options = listOf("A", "B")))
        assertTrue(RequestFormLogic.validate(s, mapOf("s" to "A")).isEmpty())
        assertEquals(FieldError.NotAChoice, RequestFormLogic.validate(s, mapOf("s" to "C"))["s"])
        assertEquals(FieldError.NotAChoice, RequestFormLogic.validate(s, mapOf("s" to "a"))["s"])
    }

    @Test fun `hidden fields are neither required nor validated`() {
        val fields = listOf(
            f("kind", FieldType.TEXT),
            f("serial", FieldType.NUMBER, required = true, rule = ShowIfRule.Equals("kind", "laptop")),
        )
        assertTrue(RequestFormLogic.validate(fields, mapOf("kind" to "phone", "serial" to "garbage")).isEmpty())
        assertEquals(FieldError.Required, RequestFormLogic.validate(fields, mapOf("kind" to "laptop"))["serial"])
    }

    @Test fun `buildAnswers sends visible non empty answers and ticked checkboxes only`() {
        val fields = listOf(
            f("kind", FieldType.TEXT),
            f("serial", FieldType.TEXT, rule = ShowIfRule.Equals("kind", "laptop")),
            f("vpn", FieldType.CHECKBOX),
            f("agree", FieldType.CHECKBOX),
            f("note", FieldType.TEXTAREA),
        )
        val sent = RequestFormLogic.buildAnswers(
            fields, mapOf("kind" to " phone ", "serial" to "SN1", "vpn" to "1", "agree" to "", "note" to "  ")
        )
        assertEquals(mapOf("kind" to "phone", "vpn" to "1"), sent)
    }

    @Test fun `isIsoDate`() {
        assertTrue(RequestFormLogic.isIsoDate("2026-10-06"))
        assertFalse(RequestFormLogic.isIsoDate("2026-10-6"))
    }
}
