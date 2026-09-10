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

/**
 * جداکننده سه‌رقمی با ارقام انگلیسی، مثلاً 5000000 -> "5,000,000"
 */
fun formatMoney(value: Double, decimals: Int = 0): String {
    val fmt = if (decimals > 0) "%,.${decimals}f" else "%,.0f"
    return String.format(Locale.US, fmt, value)
}

fun formatMoney(value: Long): String = String.format(Locale.US, "%,d", value)

/** واحد نمایش میلیون است؛ مقدار ذخیره‌شده همان عدد ورودی است (۵ → ۵) */
fun projectInputToToman(inputMillion: Double): Double = inputMillion

fun tomanToProjectInput(toman: Double): Double = toman
