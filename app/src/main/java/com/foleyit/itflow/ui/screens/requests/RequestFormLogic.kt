package com.foleyit.itflow.ui.screens.requests

import com.foleyit.itflow.data.model.CatalogField
import com.foleyit.itflow.data.model.FieldType
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Why a field is not acceptable; the UI turns these into strings. */
sealed interface FieldError {
    data object Required : FieldError
    data object MustBeTicked : FieldError
    data class TooLong(val max: Int) : FieldError
    data object NotANumber : FieldError
    data object BadDate : FieldError
    data object NotAChoice : FieldError
}

/**
 * Client-side form checks mirroring `ServiceCatalogService::validateInput`. Only visible fields are checked
 * (a hidden field is neither required nor sent); the server stays the authority.
 */
object RequestFormLogic {
    const val MAX_TEXT = 500
    const val MAX_TEXTAREA = 5000
    private const val MAX_NUMBER = 1.0E12
    // PHP is_numeric-like: no hex, no 'd'/'f' suffixes, no NaN/Infinity that Kotlin's parser would accept.
    private val NUMERIC = Regex("""[+-]?(\d+(\.\d*)?|\.\d+)([eE][+-]?\d+)?""")

    fun validate(fields: List<CatalogField>, answers: Map<String, String>): Map<String, FieldError> {
        val evaluator = ShowIfEvaluator(fields)
        val errors = LinkedHashMap<String, FieldError>()
        for (f in evaluator.visibleFields(answers)) {
            val v = answers[f.key]?.trim().orEmpty()
            if (f.type == FieldType.CHECKBOX) {
                if (f.required && ShowIfEvaluator.effectiveAnswer(f.type, v).isEmpty()) errors[f.key] = FieldError.MustBeTicked
                continue
            }
            if (v.isEmpty()) {
                if (f.required) errors[f.key] = FieldError.Required
                continue
            }
            val err: FieldError? = when (f.type) {
                FieldType.TEXT -> if (v.codePointCount(0, v.length) > MAX_TEXT) FieldError.TooLong(MAX_TEXT) else null
                FieldType.TEXTAREA -> if (v.codePointCount(0, v.length) > MAX_TEXTAREA) FieldError.TooLong(MAX_TEXTAREA) else null
                FieldType.NUMBER -> {
                    val n = if (NUMERIC.matches(v)) v.toDoubleOrNull() else null
                    if (n == null || n.isNaN() || n.isInfinite() || kotlin.math.abs(n) > MAX_NUMBER) FieldError.NotANumber else null
                }
                FieldType.DATE -> if (isIsoDate(v)) null else FieldError.BadDate
                FieldType.SELECT -> if (v in f.options) null else FieldError.NotAChoice
                FieldType.CHECKBOX -> null
            }
            if (err != null) errors[f.key] = err
        }
        return errors
    }

    /** Strict `Y-m-d`, like the server's `DateTime::createFromFormat` + round-trip check. */
    fun isIsoDate(v: String): Boolean {
        if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(v)) return false
        return try { LocalDate.parse(v).toString() == v } catch (_: DateTimeParseException) { false }
    }

    /** The `answers` object to send: visible fields only, trimmed, empty values and unticked checkboxes omitted. */
    fun buildAnswers(fields: List<CatalogField>, answers: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (f in ShowIfEvaluator(fields).visibleFields(answers)) {
            val v = answers[f.key]?.trim().orEmpty()
            if (f.type == FieldType.CHECKBOX) {
                if (ShowIfEvaluator.effectiveAnswer(f.type, v).isNotEmpty()) out[f.key] = "1"
            } else if (v.isNotEmpty()) out[f.key] = v
        }
        return out
    }
}
