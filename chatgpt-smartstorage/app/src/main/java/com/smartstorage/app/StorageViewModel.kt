package com.smartstorage.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartstorage.app.data.ScanResult
import com.smartstorage.app.data.StorageItem
import com.smartstorage.app.data.StorageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageUiState(
    val scanning: Boolean = false,
    val progress: Int = 0,
    val status: String = "برای شروع اسکن را اجرا کنید",
    val result: ScanResult = ScanResult(),
    val selected: Set<String> = emptySet(),
    val query: String = "",
    val error: String? = null
)

class StorageViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StorageRepository(application)
    private val _state = MutableStateFlow(StorageUiState())
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    fun scan() {
        if (_state.value.scanning) return
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, progress = 0, error = null, selected = emptySet()) }
            runCatching {
                repository.scan { p, s -> _state.update { it.copy(progress = p, status = s) } }
            }.onSuccess { result ->
                _state.update { it.copy(scanning = false, progress = 100, status = "اسکن کامل شد", result = result) }
            }.onFailure { t ->
                _state.update { it.copy(scanning = false, error = t.message ?: "خطای ناشناخته", status = "اسکن ناموفق بود") }
            }
        }
    }

    fun toggle(item: StorageItem) = _state.update {
        val next = it.selected.toMutableSet()
        if (!next.add(item.key)) next.remove(item.key)
        it.copy(selected = next)
    }

    fun selectRecommended() = _state.update { state ->
        state.copy(selected = state.result.items.filter { it.riskScore <= 30 }.map { it.key }.toSet())
    }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }
    fun setQuery(value: String) = _state.update { it.copy(query = value) }
}
