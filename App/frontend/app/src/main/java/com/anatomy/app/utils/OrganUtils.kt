package com.anatomy.app.utils

object OrganUtils {
    private val validSet = setOf(
        "Otak", "Kepala", "Tenggorokan", "Dada Luar", "Dada Dalam",
        "Rusuk", "Paru-Paru Kanan", "Paru-Paru Kiri", "Jantung",
        "Ginjal Luar", "Ginjal Dalam", "Hati", "Lambung", "Usus",
        "Penis", "Vagina"
    )

    fun sanitizeOrganName(rawName: String?): String {
        if (rawName.isNullOrBlank()) return ""

        val normalized = rawName
            .replace("_", " ")
            .replace(Regex("\\(.*?\\)"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("(?i)paru[- ]?paru kanan"), "Paru-Paru Kanan")
            .replace(Regex("(?i)paru[- ]?paru kiri"), "Paru-Paru Kiri")
            .replace(Regex("(?i)dada luar"), "Dada Luar")
            .replace(Regex("(?i)dada dalam"), "Dada Dalam")
            .replace(Regex("(?i)ginjal luar"), "Ginjal Luar")
            .replace(Regex("(?i)ginjal dalam"), "Ginjal Dalam")
            .replace(Regex("(?i)paru paru"), "Paru-Paru")
            .replace(Regex("(?i)paru-paru"), "Paru-Paru")
            .replace(Regex("(?i)\b(?<!-)kiri\b"), "Kiri")
            .replace(Regex("(?i)\b(?<!-)kanan\b"), "Kanan")
            .replace(Regex("\\s+"), " ")
            .trim()

        return validSet.firstOrNull { it.equals(normalized, ignoreCase = true) } ?: normalized
    }

    fun isValidOrganName(organName: String?): Boolean {
        if (organName.isNullOrBlank()) return false
        val normalized = organName.trim().replace(Regex("\\s+"), " ")
        return validSet.any { valid -> valid.equals(normalized, ignoreCase = true) }
    }
}
