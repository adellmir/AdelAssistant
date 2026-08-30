package com.adel.assistant.data

/** تبدیل ارقام فارسی/عربی به انگلیسی، تا هر عددی که با کیبورد فارسی تایپ بشه هم درست خونده بشه */
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
