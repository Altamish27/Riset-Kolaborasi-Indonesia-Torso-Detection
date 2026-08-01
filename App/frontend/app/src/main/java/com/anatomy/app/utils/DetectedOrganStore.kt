package com.anatomy.app.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * DetectedOrganStore — shared app state for the most recently detected organ.
 *
 * This is used to synchronize the last detected organ from ScanAnatomyScreen
 * into the QnA screen so users can ask follow-up questions about the detected organ.
 */
object DetectedOrganStore {
    private val _latestOrgan = MutableStateFlow<String?>(null)
    val latestOrgan: StateFlow<String?> = _latestOrgan.asStateFlow()

    fun updateOrgan(organ: String?) {
        _latestOrgan.value = organ
    }
}
