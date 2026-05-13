package com.sshautoforward.ui.hosts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sshautoforward.data.db.entity.HostEntity
import com.sshautoforward.data.repository.HostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HostListViewModel @Inject constructor(
    private val hostRepository: HostRepository,
) : ViewModel() {

    val hosts: StateFlow<List<HostEntity>> = hostRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteHost(host: HostEntity) {
        viewModelScope.launch { hostRepository.delete(host) }
    }
}
