package com.example.yawnlock.ui.permissions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.yawnlock.data.PermissionChecker
import com.example.yawnlock.data.PermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PermissionsViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(PermissionChecker.check(app))
    val state: StateFlow<PermissionState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = PermissionChecker.check(getApplication())
        }
    }
}
