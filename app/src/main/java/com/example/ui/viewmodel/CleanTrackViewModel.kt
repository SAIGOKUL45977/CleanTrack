package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.components.CapturedLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

sealed class AuthState {
    object Unauthenticated : AuthState()
    data class Authenticated(val user: UserEntity) : AuthState()
}

class CleanTrackViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CleanTrackRepository(application)
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unauthenticated)
    val authState = _authState.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()
    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating = _isAuthenticating.asStateFlow()
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError = _syncError.asStateFlow()
    private val _reportStep = MutableStateFlow(1)
    val reportStep = _reportStep.asStateFlow()
    private val _capturedPhotoUri = MutableStateFlow<Uri?>(null)
    val capturedPhotoUri = _capturedPhotoUri.asStateFlow()
    private val _capturedLocation = MutableStateFlow<CapturedLocation?>(null)
    val capturedLocation = _capturedLocation.asStateFlow()
    private val _selectedCategory = MutableStateFlow(ComplaintCategories.LIST[0])
    val selectedCategory = _selectedCategory.asStateFlow()
    private val _reportDescription = MutableStateFlow("")
    val reportDescription = _reportDescription.asStateFlow()
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()
    private val _submissionSuccessMessage = MutableStateFlow<String?>(null)
    val submissionSuccessMessage = _submissionSuccessMessage.asStateFlow()
    private var submissionId = UUID.randomUUID().toString()
    private var submissionJob: Job? = null
    private var authJob: Job? = null
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        if (repository.hasSession()) authJob = viewModelScope.launch {
            _isAuthenticating.value = true
            try { _authState.value = AuthState.Authenticated(repository.currentUser()) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { _authError.value = e.message ?: "Sign in again." }
            finally { _isAuthenticating.value = false }
        }
    }

    // Periodic refresh while this screen is subscribed. This is polling, not a Realtime subscription.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val citizenComplaints: StateFlow<List<ComplaintEntity>> = authState.flatMapLatest { state ->
        if (state is AuthState.Authenticated) {
            merge(flow { while (true) { emit(Unit); delay(10_000) } }, refreshRequests).map {
                try {
                    repository.getCitizenComplaints(state.user).also { _syncError.value = null }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { _syncError.value = e.message ?: "Unable to refresh. Check your connection."; null }
            }.filterNotNull()
        } else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000, 0), emptyList())

    fun refreshComplaints() { refreshRequests.tryEmit(Unit) }
    fun clearAuthError() { _authError.value = null }
    fun clearSubmissionMessage() { _submissionSuccessMessage.value = null }

    fun loginCitizen(emailInput: String, passwordInput: String) {
        if (_isAuthenticating.value) return
        _isAuthenticating.value = true
        authJob = viewModelScope.launch {
            try {
                require(emailInput.isNotBlank() && passwordInput.isNotBlank()) { "Enter email and password." }
                _authState.value = AuthState.Authenticated(repository.login(emailInput.trim(), passwordInput))
                _authError.value = null
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _authError.value = e.message ?: "Sign in failed." }
            finally { _isAuthenticating.value = false }
        }
    }
    fun registerCitizen(nameInput: String, emailInput: String, passwordInput: String) {
        if (_isAuthenticating.value) return
        _isAuthenticating.value = true
        authJob = viewModelScope.launch {
            try {
                require(nameInput.trim().length in 1..100 && emailInput.isNotBlank() && passwordInput.length >= 8) {
                    "Enter your name, email, and a password of at least 8 characters."
                }
                val user = repository.register(nameInput.trim(), emailInput.trim(), passwordInput)
                if (user != null) { _authState.value = AuthState.Authenticated(user); _authError.value = null }
                else _authError.value = "Check your email to confirm the account, then sign in."
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _authError.value = e.message ?: "Registration failed." }
            finally { _isAuthenticating.value = false }
        }
    }
    fun logout() {
        submissionJob?.cancel(); authJob?.cancel()
        _isSubmitting.value = false
        _submissionSuccessMessage.value = null
        repository.logout()
        _authState.value = AuthState.Unauthenticated
        _syncError.value = null
        _authError.value = null
        resetReportWizard()
    }
    fun setPhotoUri(uri: Uri?) { if (!_isSubmitting.value && _capturedPhotoUri.value != uri) { submissionId = UUID.randomUUID().toString(); _capturedPhotoUri.value = uri } }
    fun setLocation(location: CapturedLocation?) { if (!_isSubmitting.value && _capturedLocation.value != location) { submissionId = UUID.randomUUID().toString(); _capturedLocation.value = location } }
    fun setCategory(category: String) { if (!_isSubmitting.value && _selectedCategory.value != category) { submissionId = UUID.randomUUID().toString(); _selectedCategory.value = category } }
    fun setDescription(desc: String) { if (!_isSubmitting.value && _reportDescription.value != desc.take(2000)) { submissionId = UUID.randomUUID().toString(); _reportDescription.value = desc.take(2000) } }
    fun setReportStep(step: Int) { if (!_isSubmitting.value) _reportStep.value = step.coerceIn(1, 3) }
    fun resetReportWizard() {
        if (_isSubmitting.value) return
        _reportStep.value = 1; _capturedPhotoUri.value = null; _capturedLocation.value = null
        _selectedCategory.value = ComplaintCategories.LIST[0]; _reportDescription.value = ""
        submissionId = UUID.randomUUID().toString()
    }
    fun submitComplaint(context: Context) {
        if (_isSubmitting.value) return
        val photo = _capturedPhotoUri.value
        val loc = _capturedLocation.value
        if (photo == null || loc == null || _authState.value !is AuthState.Authenticated) {
            _submissionSuccessMessage.value = "Sign in, capture a photo, and capture GPS before submitting."; return
        }
        val category = _selectedCategory.value
        val description = _reportDescription.value.ifBlank { null }
        val idForRetry = submissionId
        _isSubmitting.value = true
        submissionJob = viewModelScope.launch {
            try {
                val id = repository.submitComplaint(context, idForRetry, category, photo,
                    loc.latitude, loc.longitude, loc.address, description)
                _isSubmitting.value = false
                _submissionSuccessMessage.value = "Complaint #$id submitted. Awaiting officer review."
                resetReportWizard(); refreshComplaints()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _submissionSuccessMessage.value = "Submission failed: ${e.message}. You can retry." }
            finally { _isSubmitting.value = false }
        }
    }
}
