package com.sshautoforward.ui.hosts

import android.content.pm.PackageManager
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sshautoforward.data.db.entity.HostEntity
import com.sshautoforward.data.repository.HostRepository
import com.sshautoforward.util.ReleaseChecker
import com.sshautoforward.util.ReleaseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HostListViewModel @Inject constructor(
    application: Application,
    private val hostRepository: HostRepository,
    private val releaseChecker: ReleaseChecker,
) : AndroidViewModel(application) {

    val hosts: StateFlow<List<HostEntity>> = hostRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _updateAvailable = MutableStateFlow<ReleaseInfo?>(null)
    val updateAvailable: StateFlow<ReleaseInfo?> = _updateAvailable.asStateFlow()

    init {
        checkForUpdate()
    }

    private fun checkForUpdate() {
        viewModelScope.launch {
            val currentVersion = try {
                getApplication<Application>().packageManager
                    .getPackageInfo(getApplication<Application>().packageName, 0)
                    .versionName ?: "0.0.0"
            } catch (_: Exception) {
                "0.0.0"
            }
            _updateAvailable.value = releaseChecker.check(currentVersion)
        }
    }

    fun deleteHost(host: HostEntity) {
        viewModelScope.launch { hostRepository.delete(host) }
    }
}
