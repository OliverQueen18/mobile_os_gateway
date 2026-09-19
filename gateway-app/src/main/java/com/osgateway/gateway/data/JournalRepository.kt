package com.osgateway.gateway.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JournalRepository {
    private const val MAX = 500
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.FRANCE)
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    @Synchronized
    fun append(message: String) {
        val line = "${fmt.format(Date())}  $message"
        val next = (_entries.value + line).takeLast(MAX)
        _entries.value = next
    }

    @Synchronized
    fun clear() {
        _entries.value = emptyList()
    }
}
