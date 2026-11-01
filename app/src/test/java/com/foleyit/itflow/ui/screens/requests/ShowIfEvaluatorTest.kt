package com.foleyit.itflow.ui.screens.requests

import com.foleyit.itflow.data.model.CatalogField
import com.foleyit.itflow.data.model.FieldType
import com.foleyit.itflow.data.model.ShowIfRule
import org.junit.Assert.*
import org.junit.Test

class ShowIfEvaluatorTest {

    private fun f(key: String, type: FieldType = FieldType.TEXT, rule: ShowIfRule? = null, required: Boolean = false) =
        CatalogField(key, key, type, emptyList(), required, "", rule)

    private fun keys(fields: List<CatalogField>, answers: Map<String, String>) =
        ShowIfEvaluator(fields).visibleKeys(answers)

    @Test fun `fields without a rule are always visible`() {
        assertEquals(listOf("a", "b"), keys(listOf(f("a"), f("b")), emptyMap()))
    }

    @Test fun `equals compares the whole trimmed answer`() {
        val fields = listOf(f("device"), f("serial", rule = ShowIfRule.Equals("device", "Laptop")))
        assertEquals(listOf("device"), keys(fields, mapOf("device" to "Phone")))
        assertEquals(listOf("device", "serial"), keys(fields, mapOf("device" to "Laptop")))
        assertEquals(listOf("device", "serial"), keys(fields, mapOf("device" to "  Laptop  ")))
        assertEquals(listOf("device"), keys(fields, mapOf("device" to "Laptop 2")))
        assertEquals(listOf("device"), keys(fields, mapOf("device" to "laptop"))) // case sensitive, like the server
    }

    @Test fun `equals against an empty value matches an unanswered field`() {
        val fields = listOf(f("a"), f("b", rule = ShowIfRule.Equals("a", "")))
        assertEquals(listOf("a", "b"), keys(fields, emptyMap()))
        assertEquals(listOf("a"), keys(fields, mapOf("a" to "x")))
    }

    @Test fun `in matches an exact member only`() {
        val fields = listOf(f("c"), f("d", rule = ShowIfRule.In("c", listOf("Red", "Blue"))))
        assertEquals(listOf("c", "d"), keys(fields, mapOf("c" to "Blue")))
        assertEquals(listOf("c"), keys(fields, mapOf("c" to "Green")))
        assertEquals(listOf("c"), keys(fields, mapOf("c" to "Bl")))
        assertEquals(listOf("c"), keys(fields, emptyMap()))
    }

    @Test fun `not_empty needs a non blank answer`() {
        val fields = listOf(f("a"), f("b", rule = ShowIfRule.NotEmpty("a")))
        assertEquals(listOf("a"), keys(fields, emptyMap()))
        assertEquals(listOf("a"), keys(fields, mapOf("a" to "   ")))
        assertEquals(listOf("a", "b"), keys(fields, mapOf("a" to "x")))
    }

    @Test fun `a ticked checkbox answers Yes and an unticked one is empty`() {
        val fields = listOf(
            f("vpn", FieldType.CHECKBOX),
            f("why", rule = ShowIfRule.Equals("vpn", "Yes")),
            f("any", rule = ShowIfRule.NotEmpty("vpn")),
        )
        assertEquals(listOf("vpn"), keys(fields, emptyMap()))
        assertEquals(listOf("vpn"), keys(fields, mapOf("vpn" to "0")))
        assertEquals(listOf("vpn"), keys(fields, mapOf("vpn" to "")))
        assertEquals(listOf("vpn", "why", "any"), keys(fields, mapOf("vpn" to "1")))
    }

    @Test fun `hiding a field makes dependents see an empty answer - chained rules cascade`() {
        val fields = listOf(
            f("a"),
            f("b", rule = ShowIfRule.Equals("a", "x")),
            f("c", rule = ShowIfRule.NotEmpty("b")),
        )
        // b is hidden, so even though a stale answer for b exists, c must be hidden too.
        assertEquals(listOf("a"), keys(fields, mapOf("a" to "y", "b" to "stale")))
        assertEquals(listOf("a", "b", "c"), keys(fields, mapOf("a" to "x", "b" to "v")))
        assertEquals(listOf("a", "b"), keys(fields, mapOf("a" to "x", "b" to "")))
    }

    @Test fun `a rule pointing at a missing or later field sees an empty answer`() {
        val fields = listOf(
            f("a", rule = ShowIfRule.NotEmpty("b")),
            f("b"),
            f("c", rule = ShowIfRule.NotEmpty("ghost")),
            f("d", rule = ShowIfRule.Equals("ghost", "")),
        )
        // a points at b, which comes later: not answered yet when a is evaluated, so a is hidden (server behaviour).
        assertEquals(listOf("b", "d"), keys(fields, mapOf("a" to "1", "b" to "2", "c" to "3")))
    }

    @Test fun `clearHidden drops answers of hidden fields only`() {
        val fields = listOf(f("a"), f("b", rule = ShowIfRule.Equals("a", "x")), f("c", rule = ShowIfRule.NotEmpty("b")))
        val ev = ShowIfEvaluator(fields)
        assertEquals(mapOf("a" to "y"), ev.clearHidden(mapOf("a" to "y", "b" to "1", "c" to "2")))
        assertEquals(mapOf("a" to "x", "b" to "1", "c" to "2"), ev.clearHidden(mapOf("a" to "x", "b" to "1", "c" to "2")))
        assertEquals(emptyMap<String, String>(), ShowIfEvaluator(emptyList()).clearHidden(mapOf("zzz" to "1")))
    }

    @Test fun `visibleFields keeps form order`() {
        val fields = listOf(f("a"), f("b", rule = ShowIfRule.Equals("a", "x")), f("c"))
        assertEquals(listOf("a", "c"), ShowIfEvaluator(fields).visibleFields(emptyMap()).map { it.key })
    }

    @Test fun `effectiveAnswer normalizes`() {
        assertEquals("Yes", ShowIfEvaluator.effectiveAnswer(FieldType.CHECKBOX, "1"))
        assertEquals("", ShowIfEvaluator.effectiveAnswer(FieldType.CHECKBOX, "0"))
        assertEquals("", ShowIfEvaluator.effectiveAnswer(FieldType.CHECKBOX, null))
        assertEquals("hi", ShowIfEvaluator.effectiveAnswer(FieldType.TEXT, "  hi "))
        assertEquals("", ShowIfEvaluator.effectiveAnswer(FieldType.TEXT, null))
    }

    @Test fun `ruleMet with null rule is true`() {
        assertTrue(ShowIfEvaluator.ruleMet(null, emptyMap()))
    }
}
