package com.adel.assistant.data

import java.util.Locale

fun String.toEnglishDigits(): String {
    val fa = "۰۱۲۳۴۵۶۷۸۹"
    val ar = "٠١٢٣٤٥٦٧٨٩"
    val sb = StringBuilder()
    for (ch in this) {
        val faIdx = fa.indexOf(ch)
        val arIdx = ar.indexOf(ch)
        when {
            faIdx >= 0 -> sb.append(faIdx)
            arIdx >= 0 -> sb.append(arIdx)
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

fun String.toIntOrNullFa(): Int? = this.toEnglishDigits().replace(",", "").replace("،", "").trim().toIntOrNull()
fun String.toDoubleOrNullFa(): Double? = this.toEnglishDigits().replace(",", "").replace("،", "").trim().toDoubleOrNull()

/** فقط رقم (فارسی/انگلیسی) و نقطه‌ی اعشاری را نگه می‌دارد؛ حروف حذف می‌شوند */
fun filterNumericInput(input: String): String {
    return input.filter { ch -> ch.isDigit() || ch == '.' || "۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩".contains(ch) }
}

/**
 * ورودی عددی با جداکنندهٔ هزارگان هنگام تایپ — مثلاً 1500000 → 1,500,000
 * برای فیلدهای مبلغ (مالی و مشابه).
 */
fun formatGroupedNumericInput(input: String, allowDecimal: Boolean = true): String {
    val eng = input.toEnglishDigits()
    var sawDot = false
    val raw = StringBuilder()
    for (ch in eng) {
        when {
            ch.isDigit() -> raw.append(ch)
            allowDecimal && ch == '.' && !sawDot -> {
                raw.append('.')
                sawDot = true
            }
        }
    }
    val cleaned = raw.toString()
    if (cleaned.isEmpty()) return ""
    if (cleaned == ".") return "0."
    val parts = cleaned.split('.', limit = 2)
    val intDigits = parts[0]
    val grouped = groupThousands(intDigits)
    return if (parts.size > 1) "$grouped.${parts[1]}" else grouped
}

private fun groupThousands(digits: String): String {
    if (digits.isEmpty()) return "0"
    val sb = StringBuilder()
    var count = 0
    for (i in digits.length - 1 downTo 0) {
        if (count > 0 && count % 3 == 0) sb.insert(0, ',')
        sb.insert(0, digits[i])
        count++
    }
    return sb.toString()
}

/** فرمت عدد همیشه با ارقام انگلیسی، مستقل از زبان گوشی (حیاتی برای فایل اکسل و لینک گوگل‌مپ) */
fun formatEn(fmt: String, vararg args: Any): String = String.format(Locale.US, fmt, *args)

/**
 * جداکننده سه‌رقمی با ارقام انگلیسی، مثلاً 5000000 -> "5,000,000"
 */
fun formatMoney(value: Double, decimals: Int = 2): String {
    val v = value
    if (kotlin.math.abs(v - v.toLong().toDouble()) < 1e-9) {
        return String.format(Locale.US, "%,.0f", v)
    }
    val d = decimals.coerceIn(0, 6)
    return String.format(Locale.US, "%,.${d}f", v).trimEnd('0').trimEnd('.')
}

fun formatMoney(value: Long): String = String.format(Locale.US, "%,d", value)

/** ورودی مبلغ پروژه به میلیون تومان است؛ 5 -> 5_000_000 */
const val PROJECT_AMOUNT_UNIT = 1_000_000.0

fun projectInputToToman(inputMillion: Double): Double = inputMillion * PROJECT_AMOUNT_UNIT

fun tomanToProjectInput(toman: Double): Double = toman / PROJECT_AMOUNT_UNIT
