package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
  val businessName: String = "IceTrack Wholesale",
  val contactPhone: String = "",
  val address: String = "",
  val fssaiNumber: String = "",
  val morningReminderStatus: String = "Active (8:00 AM IST Daily Summary Channel Configured)",
  val cloudBackupStatus: String = "Google Drive Sync Architecture Active • Local JSON WAL Ready",
  val isSaved: Boolean = false
)

class SettingsViewModel(
  private val settingsRepository: SettingsRepository
) : ViewModel() {

  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      settingsRepository.getSetting("business_name", "IceTrack Wholesale").collect { name ->
        _uiState.value = _uiState.value.copy(businessName = name)
      }
    }
  }

  fun updateBusinessName(name: String) {
    _uiState.value = _uiState.value.copy(businessName = name)
  }

  fun updatePhone(phone: String) {
    _uiState.value = _uiState.value.copy(contactPhone = phone)
  }

  fun updateAddress(address: String) {
    _uiState.value = _uiState.value.copy(address = address)
  }

  fun updateFssai(fssai: String) {
    _uiState.value = _uiState.value.copy(fssaiNumber = fssai)
  }

  fun saveProfile(onSuccess: () -> Unit) {
    viewModelScope.launch {
      settingsRepository.setSetting("business_name", _uiState.value.businessName)
      settingsRepository.setSetting("business_phone", _uiState.value.contactPhone)
      settingsRepository.setSetting("business_address", _uiState.value.address)
      settingsRepository.setSetting("fssai_number", _uiState.value.fssaiNumber)
      _uiState.value = _uiState.value.copy(isSaved = true)
      onSuccess()
    }
  }

  class Factory(
    private val settingsRepository: SettingsRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SettingsViewModel(settingsRepository) as T
    }
  }
}
