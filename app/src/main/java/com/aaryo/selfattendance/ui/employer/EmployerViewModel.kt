package com.aaryo.selfattendance.ui.employer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.model.Employee
import com.aaryo.selfattendance.data.model.StaffAdvance
import com.aaryo.selfattendance.data.model.StaffAttendance
import com.aaryo.selfattendance.data.model.StaffSalaryPayout
import com.aaryo.selfattendance.data.repository.StaffRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EmployerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StaffRepository(application)
    val prefs = PreferencesManager(application)

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    private val _selectedDate = MutableStateFlow(LocalDate.now().format(dateFormatter))
    val selectedDate: StateFlow<String> = _selectedDate

    private val _selectedMonth = MutableStateFlow(LocalDate.now().format(monthFormatter))
    val selectedMonth: StateFlow<String> = _selectedMonth

    private val _businessName = MutableStateFlow(prefs.businessName)
    val businessName: StateFlow<String> = _businessName

    private val _businessOwnerName = MutableStateFlow(prefs.businessOwnerName)
    val businessOwnerName: StateFlow<String> = _businessOwnerName

    private val _businessPhone = MutableStateFlow(prefs.businessPhone)
    val businessPhone: StateFlow<String> = _businessPhone

    private val _businessEmail = MutableStateFlow(prefs.businessEmail)
    val businessEmail: StateFlow<String> = _businessEmail

    private val _businessAddress = MutableStateFlow(prefs.businessAddress)
    val businessAddress: StateFlow<String> = _businessAddress

    private val _businessGstin = MutableStateFlow(prefs.businessGstin)
    val businessGstin: StateFlow<String> = _businessGstin

    private val _businessCurrency = MutableStateFlow(prefs.businessCurrency)
    val businessCurrency: StateFlow<String> = _businessCurrency

    val activeEmployees: StateFlow<List<Employee>> = repository.getAllActiveEmployees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allEmployees: StateFlow<List<Employee>> = repository.getAllEmployees()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dateAttendance: StateFlow<List<StaffAttendance>> = _selectedDate
        .flatMapLatest { date -> repository.getAttendanceForDate(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAdvances: StateFlow<List<StaffAdvance>> = repository.getAllAdvances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPendingAdvances: StateFlow<Double> = repository.getTotalPendingAdvances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthPayouts: StateFlow<List<StaffSalaryPayout>> = _selectedMonth
        .flatMapLatest { month -> repository.getPayoutsForMonth(month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _snackBarMessage = MutableSharedFlow<String>()
    val snackBarMessage: SharedFlow<String> = _snackBarMessage

    // ───────────────── Actions ─────────────────

    fun setDate(date: String) {
        _selectedDate.value = date
    }

    fun setMonth(month: String) {
        _selectedMonth.value = month
    }

    fun updateBusinessName(name: String) {
        prefs.businessName = name
        _businessName.value = name
    }

    fun updateFullBusinessProfile(
        name: String,
        owner: String,
        phone: String,
        email: String,
        address: String,
        gstin: String,
        currency: String = "₹"
    ) {
        val trimmedName = name.trim().ifBlank { "My Business" }
        val trimmedOwner = owner.trim()
        val trimmedPhone = phone.trim()
        val trimmedEmail = email.trim()
        val trimmedAddress = address.trim()
        val trimmedGstin = gstin.trim()
        val trimmedCurrency = currency.trim().ifBlank { "₹" }

        prefs.businessName = trimmedName
        prefs.businessOwnerName = trimmedOwner
        prefs.businessPhone = trimmedPhone
        prefs.businessEmail = trimmedEmail
        prefs.businessAddress = trimmedAddress
        prefs.businessGstin = trimmedGstin
        prefs.businessCurrency = trimmedCurrency

        _businessName.value = trimmedName
        _businessOwnerName.value = trimmedOwner
        _businessPhone.value = trimmedPhone
        _businessEmail.value = trimmedEmail
        _businessAddress.value = trimmedAddress
        _businessGstin.value = trimmedGstin
        _businessCurrency.value = trimmedCurrency

        viewModelScope.launch {
            _snackBarMessage.emit("Business profile updated successfully")
        }
    }

    fun saveEmployee(employee: Employee, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.saveEmployee(employee)
                _snackBarMessage.emit("Employee saved successfully")
                onComplete?.invoke()
            } catch (e: Exception) {
                _snackBarMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun deleteEmployee(id: Long) {
        viewModelScope.launch {
            try {
                repository.deleteEmployee(id)
                _snackBarMessage.emit("Employee deleted")
            } catch (e: Exception) {
                _snackBarMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun markAttendance(
        employeeId: Long,
        status: String,
        overtimeHours: Double = 0.0,
        notes: String = ""
    ) {
        viewModelScope.launch {
            try {
                repository.markAttendance(
                    employeeId = employeeId,
                    date = _selectedDate.value,
                    status = status,
                    overtimeHours = overtimeHours,
                    notes = notes
                )
            } catch (e: Exception) {
                _snackBarMessage.emit("Failed to mark attendance: ${e.message}")
            }
        }
    }

    fun markAllPresent() {
        viewModelScope.launch {
            try {
                val empList = activeEmployees.value
                if (empList.isNotEmpty()) {
                    repository.markBulkAttendance(
                        employeeIds = empList.map { it.id },
                        date = _selectedDate.value,
                        status = StaffAttendance.STATUS_PRESENT
                    )
                    _snackBarMessage.emit("All ${empList.size} staff marked Present")
                }
            } catch (e: Exception) {
                _snackBarMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun addAdvance(employeeId: Long, amount: Double, reason: String, date: String, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
                repository.addAdvance(
                    employeeId = employeeId,
                    amount = amount,
                    reason = reason.ifBlank { "Salary Advance" },
                    date = date
                )
                _snackBarMessage.emit("Advance of ₹${amount.toInt()} recorded")
                onComplete?.invoke()
            } catch (e: Exception) {
                _snackBarMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun toggleAdvanceStatus(advanceId: Long, isDeducted: Boolean, monthYear: String?) {
        viewModelScope.launch {
            try {
                repository.toggleAdvanceDeduction(advanceId, isDeducted, monthYear)
            } catch (e: Exception) {
                _snackBarMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun deleteAdvance(advance: StaffAdvance) {
        viewModelScope.launch {
            try {
                repository.deleteAdvance(advance)
                _snackBarMessage.emit("Advance record removed")
            } catch (e: Exception) {
                _snackBarMessage.emit("Error: ${e.message}")
            }
        }
    }

    suspend fun getCalculatedPayout(employeeId: Long, monthYear: String): StaffSalaryPayout {
        return repository.calculateEmployeeMonthlySalary(employeeId, monthYear)
    }

    suspend fun getEmployeeMonthAttendance(employeeId: Long, monthYear: String): List<StaffAttendance> {
        return repository.getAttendanceForEmployeeMonth(employeeId, monthYear).firstOrNull() ?: emptyList()
    }

    suspend fun getEmployeeAdvances(employeeId: Long): List<StaffAdvance> {
        return repository.getAdvancesForEmployee(employeeId).firstOrNull() ?: emptyList()
    }

    fun recordSalaryPayment(
        payout: StaffSalaryPayout,
        paidAmount: Double,
        paymentMode: String,
        notes: String,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            try {
                val isFullyPaid = paidAmount >= payout.netPayable
                val status = if (isFullyPaid) StaffSalaryPayout.STATUS_PAID else StaffSalaryPayout.STATUS_PARTIAL
                val todayStr = LocalDate.now().format(dateFormatter)

                val updatedPayout = payout.copy(
                    paidAmount = paidAmount,
                    paymentStatus = status,
                    paymentDate = todayStr,
                    paymentMode = paymentMode,
                    notes = notes
                )
                repository.saveSalaryPayout(updatedPayout)
                _snackBarMessage.emit("Salary payment of ₹${paidAmount.toInt()} saved")
                onComplete?.invoke()
            } catch (e: Exception) {
                _snackBarMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
            } catch (_: Exception) {}
            onLoggedOut()
        }
    }
}
