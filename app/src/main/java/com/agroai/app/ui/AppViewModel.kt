package com.agroai.app.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agroai.app.AppContainer
import com.agroai.app.data.Validation
import com.agroai.app.data.ValidationError
import com.agroai.app.data.model.FarmerDraft
import com.agroai.app.data.model.FarmerProfile
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.FirebaseTooManyRequestsException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UiMessage { FirebaseMissing, InvalidPhone, InvalidOtp, OtpExpired, TooManyRequests, Network, RequiredFields, SaveFailed, SignedOut, OfflineSession, LinkFailed }

data class AppUiState(
    val destination: Destination = Destination.Splash,
    val language: String = "en",
    val phoneInput: String = "",
    val otpInput: String = "",
    val draft: FarmerDraft = FarmerDraft(),
    val profile: FarmerProfile? = null,
    val onboardingStep: Int = 0,
    val errors: Set<ValidationError> = emptySet(),
    val loading: Boolean = true,
    val resendSeconds: Int = 0,
    val message: UiMessage? = null,
    val comingSoonFeature: String = "",
    val firebaseConfigured: Boolean = false,
    val offlineMode: Boolean = false,
    val sync: com.agroai.app.data.CloudSyncSummary = com.agroai.app.data.CloudSyncSummary(com.agroai.app.data.CloudSyncStatus.AuthenticationRequired),
    val localDataAvailable: Boolean = false,
    val demoMode: Boolean = false,
)

class AppViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(AppUiState(firebaseConfigured = container.firebaseConfigured))
    val state: StateFlow<AppUiState> = _state.asStateFlow()
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var countdownJob: Job? = null
    private var syncJob: Job? = null
    private var submitting = false

    init {
        viewModelScope.launch {
            container.preferences.language.collect { language ->
                _state.update { it.copy(language = language, draft = it.draft.copy(preferredLanguage = language)) }
            }
        }
        checkSession()
    }

    fun checkSession() = viewModelScope.launch {
        _state.update { it.copy(loading = true, destination = Destination.Splash, message = null) }
        if (container.preferences.loadOfflineActive()) {
            container.accountScope.useLocal()
            val draft = container.preferences.loadDraft("")
            if (container.preferences.loadOfflineProfileComplete()) {
                val profile = draft.toProfile("offline")
                _state.update { it.copy(profile = profile, draft = draft, offlineMode = true, loading = false, destination = Destination.Home) }
            } else {
                _state.update { it.copy(draft = draft, offlineMode = true, loading = false, destination = Destination.Onboarding, onboardingStep = container.preferences.loadStep().coerceIn(0, 4)) }
            }
            return@launch
        }
        val auth = container.auth
        val farmers = container.farmers
        if (auth == null || farmers == null) {
            container.accountScope.useLocal()
            _state.update { it.copy(loading = false, destination = Destination.Welcome) }
            return@launch
        }
        val uid = auth.currentUid
        if (uid == null) {
            container.accountScope.useLocal()
            _state.update { it.copy(loading = false, destination = Destination.Welcome) }
            return@launch
        }
        activateAccount(uid)
        runCatching { farmers.load(uid) }
            .onSuccess { profile -> openAuthenticatedProfile(profile) }
            .onFailure { error ->
                val cached = farmers.loadCached(uid)
                if (cached != null) {
                    openAuthenticatedProfile(cached)
                    _state.update { it.copy(message = UiMessage.OfflineSession) }
                } else _state.update { it.copy(loading = false, destination = Destination.Welcome, message = errorMessage(error)) }
            }
    }

    fun openLogin() {
        _state.update { it.copy(destination = Destination.Login, message = null) }
    }

    fun openWelcome() = _state.update { it.copy(destination = Destination.Welcome, message = null) }

    fun continueOffline() = viewModelScope.launch {
        syncJob?.cancel()
        container.accountScope.useLocal()
        container.preferences.setOfflineSession(true)
        val draft = container.preferences.loadDraft("").copy(phoneNumber = "")
        _state.update { it.copy(offlineMode = true, draft = draft, destination = Destination.Onboarding, onboardingStep = container.preferences.loadStep().coerceIn(0, 4), message = null) }
    }

    fun updatePhone(value: String) = _state.update { it.copy(phoneInput = value, message = null) }
    fun updateOtp(value: String) = _state.update { it.copy(otpInput = value.filter(Char::isDigit).take(6), message = null) }

    fun requestOtp(activity: Activity, resend: Boolean = false) {
        val auth = container.auth ?: run {
            _state.update { it.copy(message = UiMessage.FirebaseMissing) }
            return
        }
        val phone = Validation.normalizeIndianPhone(_state.value.phoneInput) ?: run {
            _state.update { it.copy(message = UiMessage.InvalidPhone) }
            return
        }
        if (resend && _state.value.resendSeconds > 0) return
        _state.update { it.copy(loading = true, message = null) }
        auth.requestOtp(activity, phone, if (resend) resendToken else null,
            object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signIn(credential)
                }
                override fun onVerificationFailed(exception: FirebaseException) {
                    _state.update { it.copy(loading = false, message = errorMessage(exception)) }
                }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    resendToken = token
                    _state.update { it.copy(loading = false, destination = Destination.Otp, phoneInput = phone) }
                    startCountdown()
                }
            }
        )
    }

    fun verifyOtp() {
        val code = _state.value.otpInput
        val id = verificationId
        if (!Validation.isValidOtp(code) || id == null) {
            _state.update { it.copy(message = if (id == null) UiMessage.OtpExpired else UiMessage.InvalidOtp) }
            return
        }
        signIn(requireNotNull(container.auth).credential(id, code))
    }

    private fun signIn(credential: PhoneAuthCredential) = viewModelScope.launch {
        val auth = container.auth ?: return@launch
        _state.update { it.copy(loading = true, message = null) }
        runCatching { auth.signIn(credential) }
            .onSuccess {
                val uid = requireNotNull(auth.currentUid)
                activateAccount(uid)
                val profile = container.farmers?.load(uid)
                openAuthenticatedProfile(profile)
            }
            .onFailure { error -> _state.update { it.copy(loading = false, message = errorMessage(error)) } }
    }

    private suspend fun openAuthenticatedProfile(profile: FarmerProfile?) {
        val auth = requireNotNull(container.auth)
        if (profile?.onboardingCompleted == true) {
            container.preferences.setLanguage(profile.preferredLanguage)
            _state.update { it.copy(profile = profile, draft = FarmerDraft.from(profile), offlineMode = false, loading = false, destination = Destination.Home) }
        } else {
            val draft = container.preferences.loadDraft(auth.currentPhone).copy(phoneNumber = auth.currentPhone)
            val step = container.preferences.loadStep().coerceIn(0, 4)
            _state.update { it.copy(profile = profile, draft = draft, offlineMode = false, onboardingStep = step, loading = false, destination = Destination.Onboarding) }
        }
    }

    private suspend fun activateAccount(uid: String) {
        container.accountScope.useAuthenticated(uid)
        _state.update { it.copy(localDataAvailable = container.localSyncQueue.hasLocalData()) }
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            container.cloudSyncManager.observe(uid).collect { summary -> _state.update { it.copy(sync = summary) } }
        }
        container.cloudSyncManager.schedule(uid)
    }

    fun changeLanguage(language: String) {
        if (language !in setOf("en", "hi")) return
        _state.update { it.copy(language = language, draft = it.draft.copy(preferredLanguage = language)) }
        viewModelScope.launch {
            container.preferences.setLanguage(language)
            saveDraft()
            val profile = _state.value.profile ?: return@launch
            if (_state.value.offlineMode) {
                container.preferences.saveDraft(FarmerDraft.from(profile.copy(preferredLanguage = language)))
                _state.update { it.copy(profile = profile.copy(preferredLanguage = language)) }
            } else runCatching { container.farmers?.save(profile.copy(preferredLanguage = language)) }
                .onSuccess { _state.update { it.copy(profile = profile.copy(preferredLanguage = language)) } }
        }
    }

    fun updateDraft(change: (FarmerDraft) -> FarmerDraft) {
        _state.update { it.copy(draft = change(it.draft), errors = emptySet(), message = null) }
        viewModelScope.launch { saveDraft() }
    }

    fun nextOnboardingStep() {
        val current = _state.value
        val errors = when (current.onboardingStep) {
            0 -> Validation.personal(current.draft).let { if (current.offlineMode) it - ValidationError.Phone else it }
            1 -> Validation.location(current.draft)
            2 -> Validation.farm(current.draft)
            3 -> Validation.preferences(current.draft)
            else -> emptySet()
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, message = UiMessage.RequiredFields) }
            return
        }
        if (current.onboardingStep == 4) saveProfile() else setOnboardingStep(current.onboardingStep + 1)
    }

    fun previousOnboardingStep() {
        val step = _state.value.onboardingStep
        if (step > 0) setOnboardingStep(step - 1)
    }

    fun editOnboardingStep(step: Int) = setOnboardingStep(step.coerceIn(0, 4))

    private fun setOnboardingStep(step: Int) {
        _state.update { it.copy(onboardingStep = step, errors = emptySet(), message = null) }
        viewModelScope.launch { container.preferences.saveStep(step); saveDraft() }
    }

    fun saveProfile() {
        if (submitting) return
        val errors = Validation.profile(_state.value.draft).let { if (_state.value.offlineMode) it - ValidationError.Phone else it }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, message = UiMessage.RequiredFields) }
            return
        }
        if (_state.value.offlineMode) {
            submitting = true
            viewModelScope.launch {
                val profile = _state.value.draft.toProfile("offline", _state.value.profile)
                container.preferences.saveDraft(FarmerDraft.from(profile))
                container.preferences.setOfflineSession(true, true)
                _state.update { it.copy(profile = profile, draft = FarmerDraft.from(profile), loading = false, destination = Destination.Home) }
                submitting = false
            }
            return
        }
        val uid = container.auth?.currentUid ?: return
        submitting = true
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            val profile = _state.value.draft.toProfile(uid, _state.value.profile)
            runCatching { requireNotNull(container.farmers).save(profile); requireNotNull(container.farmers).load(uid) }
                .onSuccess { saved ->
                    container.preferences.clearDraft()
                    _state.update { it.copy(profile = saved ?: profile, draft = FarmerDraft.from(saved ?: profile), loading = false, destination = Destination.Home) }
                }
                .onFailure { error -> _state.update { it.copy(loading = false, message = errorMessage(error)) } }
            submitting = false
        }
    }

    fun openEditProfile() {
        val profile = _state.value.profile ?: return
        _state.update { it.copy(draft = FarmerDraft.from(profile), errors = emptySet(), destination = Destination.EditProfile) }
    }

    fun navigate(destination: Destination) = _state.update { it.copy(destination = destination, message = null) }

    fun openComingSoon(feature: String) = _state.update { it.copy(destination = Destination.ComingSoon, comingSoonFeature = feature) }

    fun startDemo() = viewModelScope.launch {
        syncJob?.cancel()
        _state.update { it.copy(loading = true, message = null) }
        runCatching { container.demoData.reset() }
            .onSuccess {
                val profile = container.demoData.profile.copy(preferredLanguage = _state.value.language)
                _state.update { it.copy(profile = profile, draft = FarmerDraft.from(profile), offlineMode = true, demoMode = true, loading = false, destination = Destination.Home) }
            }
            .onFailure { _state.update { it.copy(loading = false, message = UiMessage.SaveFailed) } }
    }

    fun resetDemo() { if (_state.value.demoMode) startDemo() }

    fun logout() {
        val wasOffline = _state.value.offlineMode
        val wasDemo = _state.value.demoMode
        if (!wasOffline) container.auth?.signOut()
        syncJob?.cancel()
        container.accountScope.useLocal()
        viewModelScope.launch { if (wasOffline && !wasDemo) container.preferences.setOfflineSession(false) else if (!wasDemo) container.preferences.clearDraft() }
        verificationId = null; resendToken = null; countdownJob?.cancel()
        _state.update { AppUiState(destination = Destination.Welcome, language = it.language, firebaseConfigured = container.firebaseConfigured, loading = false, message = UiMessage.SignedOut) }
    }

    fun linkLocalData() {
        val uid = container.auth?.currentUid ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching { container.localSyncQueue.linkLocalData(uid) }
                .onSuccess { _state.update { it.copy(loading = false, localDataAvailable = false) } }
                .onFailure { _state.update { it.copy(loading = false, message = UiMessage.LinkFailed) } }
        }
    }

    fun keepLocalDataSeparate() = _state.update { it.copy(localDataAvailable = false) }

    fun retrySync() {
        val uid = container.auth?.currentUid ?: return
        viewModelScope.launch { container.cloudSyncManager.retry(uid) }
    }

    fun keepLocalConflictVersion() {
        val uid = container.auth?.currentUid ?: return
        viewModelScope.launch { container.cloudSyncManager.keepLocalConflicts(uid) }
    }

    fun useCloudConflictVersion() {
        val uid = container.auth?.currentUid ?: return
        viewModelScope.launch { container.cloudSyncManager.useCloudConflicts(uid, container.remoteRecordApplier) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private suspend fun saveDraft() = container.preferences.saveDraft(_state.value.draft)

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (second in 60 downTo 0) {
                _state.update { it.copy(resendSeconds = second) }
                delay(1_000)
            }
        }
    }

    private fun errorMessage(error: Throwable): UiMessage = when (error) {
        is FirebaseTooManyRequestsException -> UiMessage.TooManyRequests
        is FirebaseNetworkException -> UiMessage.Network
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException -> UiMessage.InvalidOtp
        else -> error.cause?.takeIf { it !== error }?.let(::errorMessage) ?: UiMessage.SaveFailed
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
}
