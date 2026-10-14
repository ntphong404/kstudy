package vn.edu.actvn.kstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import vn.edu.actvn.kstudy.data.model.PersonalInfo
import vn.edu.actvn.kstudy.data.model.User
import vn.edu.actvn.kstudy.data.repository.PasswordChange
import vn.edu.actvn.kstudy.data.repository.UserRepository
import vn.edu.actvn.kstudy.util.ProfileRules

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserRepository(application.applicationContext)

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _loginResult = MutableLiveData<Pair<Boolean, String>>()
    val loginResult: LiveData<Pair<Boolean, String>> = _loginResult

    // Outcome of the last profile update; the profile screen clears it once it has shown it
    private val _profileResult = MutableLiveData<Pair<Boolean, String>?>()
    val profileResult: LiveData<Pair<Boolean, String>?> = _profileResult

    // Outcome of the last password change; the profile screen clears it once it has handled it
    private val _passwordResult = MutableLiveData<PasswordChange?>()
    val passwordResult: LiveData<PasswordChange?> = _passwordResult

    // The password the user has just signed in with. An account whose password an admin chose
    // must replace it before going any further, and must not be asked to type it twice.
    var signedInPassword: String = ""
        private set

    fun login(username: String, pass: String) {
        if (username.isBlank() || pass.isBlank()) {
            _loginResult.value = Pair(false, "Vui lòng điền đầy đủ thông tin!")
            return
        }
        viewModelScope.launch {
            val user = try {
                repository.login(username, pass)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Without this the app crashes when there is no network or Firestore rejects the request
                _loginResult.value = Pair(false, loginErrorMessage(e))
                return@launch
            }
            if (user != null) {
                signedInPassword = pass
                _currentUser.value = user
                _loginResult.value = Pair(true, "Đăng nhập thành công!")
            } else {
                _loginResult.value = Pair(false, "Tài khoản hoặc mật khẩu không chính xác!")
            }
        }
    }

    private fun loginErrorMessage(e: Exception): String {
        val detail = generateSequence<Throwable>(e) { it.cause }.joinToString(" ") { it.message.orEmpty() }
        return if ("PERMISSION_DENIED" in detail) {
            "Firestore từ chối truy cập. Hãy kiểm tra Rules trên Firebase!"
        } else {
            "Không kết nối được máy chủ. Vui lòng kiểm tra mạng và thử lại!"
        }
    }

    fun initializeMockData() {
        viewModelScope.launch {
            repository.initializeMockDataIfNeeded()
        }
    }

    fun logout() {
        signedInPassword = ""
        _currentUser.value = null
    }

    fun refreshProfile() {
        val current = _currentUser.value ?: return
        viewModelScope.launch {
            val fresh = repository.getUserProfile(current.id)
            if (fresh != null) {
                _currentUser.value = fresh
            }
        }
    }

    fun loadUser(userId: String) {
        viewModelScope.launch {
            val user = repository.getUserProfile(userId)
            if (user != null) {
                _currentUser.value = user
            }
        }
    }

    fun updateProfile(fullName: String, phone: String, personal: PersonalInfo) {
        val current = _currentUser.value ?: return
        if (fullName.isBlank()) {
            _profileResult.value = Pair(false, "Họ tên không được để trống!")
            return
        }
        if (!ProfileRules.isValidEmail(personal.email)) {
            _profileResult.value = Pair(false, ProfileRules.INVALID_EMAIL)
            return
        }
        viewModelScope.launch {
            val ok = repository.updateUserProfile(current.id, fullName, phone, personal)
            if (ok) {
                refreshProfile()
                _profileResult.value = Pair(true, "Cập nhật hồ sơ thành công!")
            } else {
                _profileResult.value = Pair(false, "Cập nhật thất bại!")
            }
        }
    }

    fun clearProfileResult() {
        _profileResult.value = null
    }

    // The dialog has already checked the new password against PasswordRules
    fun changePassword(currentPassword: String, newPassword: String) {
        val current = _currentUser.value
        if (current == null) {
            _passwordResult.value = PasswordChange.FAILED
            return
        }
        viewModelScope.launch {
            _passwordResult.value = repository.changePassword(current.id, currentPassword, newPassword)
        }
    }

    fun clearPasswordResult() {
        _passwordResult.value = null
    }
}
