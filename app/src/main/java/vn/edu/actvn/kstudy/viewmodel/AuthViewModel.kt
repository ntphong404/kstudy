package vn.edu.actvn.kstudy.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import vn.edu.actvn.kstudy.data.model.User
import vn.edu.actvn.kstudy.data.repository.UserRepository

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserRepository(application.applicationContext)

    private val _currentUser = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _loginResult = MutableLiveData<Pair<Boolean, String>>()
    val loginResult: LiveData<Pair<Boolean, String>> = _loginResult

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

    fun updateProfile(fullName: String, phone: String) {
        val current = _currentUser.value ?: return
        if (fullName.isBlank()) {
            _loginResult.value = Pair(false, "Họ tên không được để trống!")
            return
        }
        viewModelScope.launch {
            val ok = repository.updateUserProfile(current.id, fullName, phone)
            if (ok) {
                refreshProfile()
                _loginResult.value = Pair(true, "Cập nhật hồ sơ thành công!")
            } else {
                _loginResult.value = Pair(false, "Cập nhật thất bại!")
            }
        }
    }
}
