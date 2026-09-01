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

fun String.toIntOrNullFa(): Int? = this.toEnglishDigits().trim().toIntOrNull()
fun String.toDoubleOrNullFa(): Double? = this.toEnglishDigits().trim().toDoubleOrNull()

/** فقط رقم (فارسی/انگلیسی) و نقطه‌ی اعشاری را نگه می‌دارد؛ حروف حذف می‌شوند */
fun filterNumericInput(input: String): String {
    return input.filter { ch -> ch.isDigit() || ch == '.' || "۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩".contains(ch) }
}

/** فرمت عدد همیشه با ارقام انگلیسی، مستقل از زبان گوشی (حیاتی برای فایل اکسل و لینک گوگل‌مپ) */
fun formatEn(fmt: String, vararg args: Any): String = String.format(Locale.US, fmt, *args)
