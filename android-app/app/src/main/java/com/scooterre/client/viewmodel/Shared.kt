package com.scooterre.client.viewmodel

import android.app.Application
import android.content.SharedPreferences
import com.scooterre.client.ui.strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/** What the feature controllers share with the ViewModel: the UI state, the preferences and a scope. */
internal class Shared(
    val app: Application,
    val scope: CoroutineScope,
    val state: MutableStateFlow<UiState>,
    val prefs: SharedPreferences,
) {
    val s get() = strings(state.value.language)
}
