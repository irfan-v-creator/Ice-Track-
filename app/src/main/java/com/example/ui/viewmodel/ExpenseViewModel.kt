package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ExpenseRepository
import com.example.domain.model.Expense
import com.example.domain.model.ExpenseCategory
import com.example.domain.model.PaymentMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpenseViewModel(
  private val expenseRepo: ExpenseRepository
) : ViewModel() {

  private val _isSubmitting = MutableStateFlow(false)
  val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

  val allExpenses = expenseRepo.allExpenses.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  fun recordExpense(
    category: ExpenseCategory,
    amountPaise: Long,
    expenseDate: Long,
    paymentMethod: PaymentMethod,
    referenceNumber: String,
    notes: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
  ) {
    if (_isSubmitting.value) return
    viewModelScope.launch {
      _isSubmitting.value = true
      try {
        expenseRepo.recordExpense(
          category = category,
          amountPaise = amountPaise,
          expenseDate = expenseDate,
          paymentMethod = paymentMethod,
          referenceNumber = referenceNumber,
          notes = notes
        )
        onSuccess()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to record expense")
      } finally {
        _isSubmitting.value = false
      }
    }
  }

  fun softDeleteExpense(expenseId: Long, onError: (String) -> Unit = {}) {
    viewModelScope.launch {
      try {
        expenseRepo.softDelete(expenseId)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        onError(e.message ?: "Failed to delete expense")
      }
    }
  }

  class Factory(
    private val expenseRepo: ExpenseRepository
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return ExpenseViewModel(expenseRepo) as T
    }
  }
}
