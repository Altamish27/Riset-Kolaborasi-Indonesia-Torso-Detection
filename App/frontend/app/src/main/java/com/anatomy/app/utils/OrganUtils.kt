package com.anatomy.app.utils

object OrganUtils {
    private val validSet = setOf(
        "Otak", "Kepala", "Tenggorokan", "Dada Luar", "Dada Dalam",
        "Rusuk", "Paru-Paru Kanan", "Paru-Paru Kiri", "Jantung",
        "Ginjal Luar", "Ginjal Dalam", "Hati", "Lambung", "Usus",
        "Penis", "Vagina"
    )

    fun isValidOrganName(organName: String): Boolean {
        return validSet.contains(organName)
    }

    fun sanitizeOrganName(rawName: String): String {
        return rawName
            .replace("_", " ")
            .replace(Regex("\\(.*?\\)"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("(?i)paru paru kanan"), "Paru-Paru Kanan")
            .replace(Regex("(?i)paru paru kiri"), "Paru-Paru Kiri")
            .replace(Regex("(?i)dada luar"), "Dada Luar")
            .replace(Regex("(?i)dada dalam"), "Dada Dalam")
    }
}
