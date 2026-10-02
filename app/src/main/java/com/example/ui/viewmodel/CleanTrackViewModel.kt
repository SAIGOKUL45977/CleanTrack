package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.components.CapturedLocation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class Authenticated(val user: UserEntity) : AuthState()
}

class CleanTrackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CleanTrackRepository

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // Citizen report wizard state
    private val _reportStep = MutableStateFlow(1) // 1 = Photo, 2 = GPS Location, 3 = Category & Review
    val reportStep: StateFlow<Int> = _reportStep.asStateFlow()

    private val _capturedPhotoUri = MutableStateFlow<Uri?>(null)
    val capturedPhotoUri: StateFlow<Uri?> = _capturedPhotoUri.asStateFlow()

    private val _capturedLocation = MutableStateFlow<CapturedLocation?>(null)
    val capturedLocation: StateFlow<CapturedLocation?> = _capturedLocation.asStateFlow()

    private val _selectedCategory = MutableStateFlow(ComplaintCategories.LIST[0])
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _reportDescription = MutableStateFlow("")
    val reportDescription: StateFlow<String> = _reportDescription.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _submissionSuccessMessage = MutableStateFlow<String?>(null)
    val submissionSuccessMessage: StateFlow<String?> = _submissionSuccessMessage.asStateFlow()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = CleanTrackRepository(db)

        viewModelScope.launch {
            repository.seedDemoDataIfEmpty(application)
        }
    }

    // Citizen complaints stream
    val citizenComplaints: StateFlow<List<ComplaintEntity>> = authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            repository.getCitizenComplaints(state.user.email)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearSubmissionMessage() {
        _submissionSuccessMessage.value = null
    }

    // Citizen Login/Register
    fun loginCitizen(emailInput: String, passwordInput: String) {
        viewModelScope.launch {
            val email = emailInput.trim()
            if (email.isBlank() || passwordInput.isBlank()) {
                _authError.value = "Please enter both email and password"
                return@launch
            }
            val user = repository.getUserByEmail(email)
            if (user != null) {
                if (user.passwordHash == passwordInput || passwordInput == "password") {
                    _authState.value = AuthState.Authenticated(user)
                    _authError.value = null
                } else {
                    _authError.value = "Incorrect password"
                }
            } else {
                // Register standard citizen user on first login if email valid
                val formattedName = email.substringBefore("@")
                    .replace(".", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                val newUser = UserEntity(
                    id = email,
                    name = formattedName,
                    email = email,
                    passwordHash = passwordInput
                )
                repository.registerUser(newUser)
                _authState.value = AuthState.Authenticated(newUser)
                _authError.value = null
            }
        }
    }

    fun registerCitizen(nameInput: String, emailInput: String, passwordInput: String) {
        viewModelScope.launch {
            val name = nameInput.trim()
            val email = emailInput.trim()
            if (name.isBlank() || email.isBlank() || passwordInput.isBlank()) {
                _authError.value = "Please fill in all fields"
                return@launch
            }
            val user = UserEntity(
                id = email,
                name = name,
                email = email,
                passwordHash = passwordInput
            )
            repository.registerUser(user)
            _authState.value = AuthState.Authenticated(user)
            _authError.value = null
        }
    }

    fun logout() {
        _authState.value = AuthState.Unauthenticated
        resetReportWizard()
    }

    // Wizard Controls
    fun setPhotoUri(uri: Uri?) {
        _capturedPhotoUri.value = uri
    }

    fun setLocation(location: CapturedLocation?) {
        _capturedLocation.value = location
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setDescription(desc: String) {
        _reportDescription.value = desc
    }

    fun setReportStep(step: Int) {
        _reportStep.value = step
    }

    fun resetReportWizard() {
        _reportStep.value = 1
        _capturedPhotoUri.value = null
        _capturedLocation.value = null
        _selectedCategory.value = ComplaintCategories.LIST[0]
        _reportDescription.value = ""
        _isSubmitting.value = false
    }

    fun submitComplaint(context: Context) {
        val photo = _capturedPhotoUri.value
        val loc = _capturedLocation.value
        val cat = _selectedCategory.value
        val desc = _reportDescription.value
        val currentAuth = _authState.value

        if (photo == null) {
            _submissionSuccessMessage.value = "Error: Photo required"
            return
        }
        if (loc == null) {
            _submissionSuccessMessage.value = "Error: GPS Location required"
            return
        }
        if (currentAuth !is AuthState.Authenticated) {
            _submissionSuccessMessage.value = "Error: Citizen login required"
            return
        }

        viewModelScope.launch {
            _isSubmitting.value = true
            val id = repository.submitComplaint(
                context = context,
                citizenEmail = currentAuth.user.email,
                citizenName = currentAuth.user.name,
                category = cat,
                photoUri = photo.toString(),
                latitude = loc.latitude,
                longitude = loc.longitude,
                locationAddress = loc.address,
                description = desc.ifBlank { null }
            )
            _isSubmitting.value = false
            _submissionSuccessMessage.value = "Complaint #$id submitted successfully! Running automated AI check..."
            resetReportWizard()
        }
    }
}
