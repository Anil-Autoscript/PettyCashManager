package com.pettycash.manager.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pettycash.manager.data.local.PreferenceManager
import com.pettycash.manager.data.model.LoginResponse
import com.pettycash.manager.data.repository.AuthRepository
import com.pettycash.manager.databinding.ActivityLoginBinding
import com.pettycash.manager.ui.main.MainActivity
import com.pettycash.manager.utils.Result
import kotlinx.coroutines.launch

// ─── ViewModel ────────────────────────────────────────────────────────────────
class LoginViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _state = androidx.lifecycle.MutableLiveData<LoginState>()
    val state: androidx.lifecycle.LiveData<LoginState> = _state

    fun login(username: String, password: String, companyId: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.value = LoginState.Error("Username and password are required")
            return
        }
        _state.value = LoginState.Loading
        viewModelScope.launch {
            when (val result = repo.login(username, password, companyId)) {
                is Result.Success -> _state.value = LoginState.Success(result.data)
                is Result.Error -> _state.value = LoginState.Error(result.message)
                else -> {}
            }
        }
    }

    sealed class LoginState {
        object Loading : LoginState()
        data class Success(val response: LoginResponse) : LoginState()
        data class Error(val message: String) : LoginState()
    }
}

class LoginViewModelFactory(private val repo: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LoginViewModel(repo) as T
    }
}

// ─── Activity ─────────────────────────────────────────────────────────────────
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: PreferenceManager

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(AuthRepository(this))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getInstance(this)

        // Pre-fill company name
        binding.tvCompanyName.text = prefs.companyName.ifEmpty { "Your Company" }

        // Load company logo if exists
        if (prefs.logoUrl.isNotEmpty()) {
            com.bumptech.glide.Glide.with(this)
                .load(prefs.logoUrl)
                .circleCrop()
                .placeholder(com.pettycash.manager.R.drawable.ic_company_placeholder)
                .into(binding.imgCompanyLogo)
        }

        setupObservers()
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString()
            viewModel.login(username, password, prefs.companyId)
        }
    }

    private fun setupObservers() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is LoginViewModel.LoginState.Loading -> showLoading(true)
                is LoginViewModel.LoginState.Success -> {
                    showLoading(false)
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                is LoginViewModel.LoginState.Error -> {
                    showLoading(false)
                    binding.tvError.text = state.message
                    binding.tvError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.tvError.visibility = View.GONE
    }
}
