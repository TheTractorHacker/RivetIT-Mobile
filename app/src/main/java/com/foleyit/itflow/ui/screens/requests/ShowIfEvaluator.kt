package com.foleyit.itflow.ui.screens.requests

import com.foleyit.itflow.data.model.CatalogField
import com.foleyit.itflow.data.model.FieldType
import com.foleyit.itflow.data.model.ShowIfRule

/**
 * Live evaluation of a request form's conditional fields. Mirrors the server's
 * `ServiceCatalogService::showIfMet` / `validateInput` and the web form's `js/catalog_show_if.js`:
 *
 * - A rule points at an EARLIER field; fields are walked in form order, so one pass is enough.
 * - A hidden field counts as an empty answer for every rule that points at it (so hiding cascades).
 * - A checkbox's answer is `"Yes"` when ticked and empty otherwise; other answers are trimmed.
 * - `equals` compares the whole answer, `in` is an exact match against the list, `not_empty` is "answered".
 * - A field with no (or an unusable) rule is always shown.
 *
 * Form answers are held as raw strings per field key; a ticked checkbox is any non-empty value other than "0".
 */
class ShowIfEvaluator(private val fields: List<CatalogField>) {

    /** Keys of the fields that are visible for [answers], in form order. */
    fun visibleKeys(answers: Map<String, String>): List<String> {
        val effective = HashMap<String, String>()
        val visible = ArrayList<String>()
        for (f in fields) {
            if (!ruleMet(f.showIf, effective)) continue
            visible += f.key
            effective[f.key] = effectiveAnswer(f.type, answers[f.key])
        }
        return visible
    }

    fun visibleFields(answers: Map<String, String>): List<CatalogField> {
        val keys = visibleKeys(answers).toSet()
        return fields.filter { it.key in keys }
    }

    /** [answers] without the entries of hidden fields (hidden fields are cleared, not just collapsed). */
    fun clearHidden(answers: Map<String, String>): Map<String, String> {
        val keys = visibleKeys(answers).toSet()
        return answers.filterKeys { it in keys }
    }

    companion object {
        /** The answer a rule sees: trimmed text, or `Yes`/empty for a checkbox. */
        fun effectiveAnswer(type: FieldType, raw: String?): String {
            val v = raw?.trim().orEmpty()
            return if (type == FieldType.CHECKBOX) (if (v.isNotEmpty() && v != "0") "Yes" else "") else v
        }

        fun ruleMet(rule: ShowIfRule?, effective: Map<String, String>): Boolean {
            if (rule == null) return true
            val v = effective[rule.field].orEmpty()
            return when (rule) {
                is ShowIfRule.Equals -> v == rule.value
                is ShowIfRule.In -> v in rule.values
                is ShowIfRule.NotEmpty -> v.isNotEmpty()
            }
        }
    }
}
