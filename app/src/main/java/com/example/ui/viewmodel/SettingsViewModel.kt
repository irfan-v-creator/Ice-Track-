package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.IceTrackDatabase
import com.example.data.repository.SettingsRepository
import com.example.service.BackupRestoreService
import com.example.service.BackupValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
  val businessName: String = "IceTrack Wholesale",
  val contactPhone: String = "",
  val address: String = "",
  val fssaiNumber: String = "",
  val morningReminderStatus: String = "Active (8:00 AM IST Daily Summary Channel Configured)",
  val cloudBackupStatus: String = "Schema V5 Full 25-Table JSON Backup & Restore Ready",
  val isSaved: Boolean = false,
  val isBackupBusy: Boolean = false,
  val backupMessage: String? = null,
  val pendingRestoreValidation: BackupValidationResult? = null,
  val pendingRestoreJson: String? = null
)

class SettingsViewModel(
  private val settingsRepository: SettingsRepository,
  private val database: IceTrackDatabase? = null
) : ViewModel() {

  private val _uiState = MutableStateFlow(SettingsUiState())
  val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      settingsRepository.getSetting("business_name", "IceTrack Wholesale").collect { name ->
        _uiState.value = _uiState.value.copy(businessName = name)
      }
    }
    viewModelScope.launch {
      settingsRepository.getSetting("business_phone", "").collect { phone ->
        _uiState.value = _uiState.value.copy(contactPhone = phone)
      }
    }
    viewModelScope.launch {
      settingsRepository.getSetting("business_address", "").collect { addr ->
        _uiState.value = _uiState.value.copy(address = addr)
      }
    }
    viewModelScope.launch {
      settingsRepository.getSetting("fssai_number", "").collect { fssai ->
        _uiState.value = _uiState.value.copy(fssaiNumber = fssai)
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

  fun exportBackupToUri(context: Context, uri: Uri) {
    val db = database
    if (db == null) {
      _uiState.value = _uiState.value.copy(backupMessage = "Database reference unavailable for backup export.")
      return
    }
    if (_uiState.value.isBackupBusy) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isBackupBusy = true, backupMessage = null)
      try {
        val json = withContext(Dispatchers.IO) {
          val payload = BackupRestoreService.exportDatabaseFromDatabase(db)
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(payload.toByteArray(Charsets.UTF_8))
            out.flush()
          } ?: error("Unable to open destination file for writing backup.")
          payload
        }
        val summary = BackupRestoreService.validateBackupJson(json)
        _uiState.value = _uiState.value.copy(
          isBackupBusy = false,
          backupMessage = "Backup exported (Schema v${summary.schemaVersion}, ${summary.salesCount} sales, ${summary.purchaseCount} purchases, ${summary.auditLogCount} audit logs)."
        )
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          isBackupBusy = false,
          backupMessage = "Backup export failed: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun inspectBackupFromUri(context: Context, uri: Uri) {
    if (_uiState.value.isBackupBusy) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isBackupBusy = true, backupMessage = null)
      try {
        val (rawJson, validation) = withContext(Dispatchers.IO) {
          val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.bufferedReader(Charsets.UTF_8).readText()
          } ?: error("Unable to read selected backup file.")
          text to BackupRestoreService.validateBackupJson(text)
        }
        if (!validation.isValid) {
          _uiState.value = _uiState.value.copy(
            isBackupBusy = false,
            backupMessage = validation.errorMessage ?: "Invalid backup file."
          )
        } else {
          _uiState.value = _uiState.value.copy(
            isBackupBusy = false,
            pendingRestoreValidation = validation,
            pendingRestoreJson = rawJson
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          isBackupBusy = false,
          backupMessage = "Could not inspect backup: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun dismissRestorePreview() {
    _uiState.value = _uiState.value.copy(
      pendingRestoreValidation = null,
      pendingRestoreJson = null
    )
  }

  fun confirmRestoreBackup(reason: String = "Manual SAF backup restore from Settings") {
    val db = database ?: run {
      _uiState.value = _uiState.value.copy(backupMessage = "Database reference unavailable for restore.")
      return
    }
    val json = _uiState.value.pendingRestoreJson ?: return
    if (_uiState.value.isBackupBusy) return
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isBackupBusy = true, backupMessage = null)
      try {
        val result = withContext(Dispatchers.IO) {
          BackupRestoreService.restoreDatabaseFromJson(
            db = db,
            jsonString = json,
            reason = reason
          )
        }
        if (result.isValid) {
          _uiState.value = _uiState.value.copy(
            isBackupBusy = false,
            pendingRestoreValidation = null,
            pendingRestoreJson = null,
            backupMessage = "Restore complete: ${result.productCount} products, ${result.customerCount} shops, ${result.salesCount} sales, ${result.purchaseCount} purchases restored."
          )
        } else {
          _uiState.value = _uiState.value.copy(
            isBackupBusy = false,
            pendingRestoreValidation = null,
            pendingRestoreJson = null,
            backupMessage = result.errorMessage ?: "Restore failed."
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _uiState.value = _uiState.value.copy(
          isBackupBusy = false,
          pendingRestoreValidation = null,
          pendingRestoreJson = null,
          backupMessage = "Restore failed: ${e.message ?: "Unknown error"}"
        )
      }
    }
  }

  fun clearBackupMessage() {
    _uiState.value = _uiState.value.copy(backupMessage = null)
  }

  class Factory(
    private val settingsRepository: SettingsRepository,
    private val database: IceTrackDatabase? = null
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return SettingsViewModel(settingsRepository, database) as T
    }
  }
}
