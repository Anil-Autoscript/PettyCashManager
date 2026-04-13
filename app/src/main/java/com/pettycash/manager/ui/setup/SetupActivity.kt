package com.pettycash.manager.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bumptech.glide.Glide
import com.pettycash.manager.R
import com.pettycash.manager.data.local.PreferenceManager
import com.pettycash.manager.data.repository.AuthRepository
import com.pettycash.manager.databinding.ActivitySetupBinding
import com.pettycash.manager.ui.login.LoginActivity
import com.pettycash.manager.utils.ImageUtils
import com.pettycash.manager.utils.Result
import kotlinx.coroutines.launch

// ─── ViewModel ────────────────────────────────────────────────────────────────
class SetupViewModel(private val repo: AuthRepository) : ViewModel() {

    private val _state = androidx.lifecycle.MutableLiveData<SetupState>()
    val state: androidx.lifecycle.LiveData<SetupState> = _state

    fun setupCompany(name: String, adminUser: String, adminPass: String, logoBase64: String = "") {
        if (name.isBlank() || adminUser.isBlank() || adminPass.isBlank()) {
            _state.value = SetupState.Error("Please fill all required fields")
            return
        }
        if (adminPass.length < 6) {
            _state.value = SetupState.Error("Password must be at least 6 characters")
            return
        }
        _state.value = SetupState.Loading
        viewModelScope.launch {
            when (val result = repo.setupCompany(name, adminUser, adminPass)) {
                is Result.Success -> _state.value = SetupState.Success(result.data)
                is Result.Error -> _state.value = SetupState.Error(result.message)
                else -> {}
            }
        }
    }

    sealed class SetupState {
        object Loading : SetupState()
        data class Success(val companyId: String) : SetupState()
        data class Error(val message: String) : SetupState()
    }
}

class SetupViewModelFactory(private val repo: AuthRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SetupViewModel(repo) as T
    }
}

// ─── Activity ─────────────────────────────────────────────────────────────────
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private var selectedLogoUri: Uri? = null
    private var logoBase64: String = ""

    private val viewModel: SetupViewModel by viewModels {
        SetupViewModelFactory(AuthRepository(this))
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedLogoUri = it
            logoBase64 = ImageUtils.uriToBase64(this, it)
            Glide.with(this).load(it).circleCrop().into(binding.imgLogo)
            binding.tvUploadHint.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupObservers()
        setupListeners()
    }

    private fun setupListeners() {
        binding.cardLogo.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.btnSetup.setOnClickListener {
            viewModel.setupCompany(
                name = binding.etCompanyName.text.toString().trim(),
                adminUser = binding.etAdminUsername.text.toString().trim(),
                adminPass = binding.etAdminPassword.text.toString(),
                logoBase64 = logoBase64
            )
        }
    }

    private fun setupObservers() {
        viewModel.state.observe(this) { state ->
            when (state) {
                is SetupViewModel.SetupState.Loading -> showLoading(true)
                is SetupViewModel.SetupState.Success -> {
                    showLoading(false)
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                is SetupViewModel.SetupState.Error -> {
                    showLoading(false)
                    binding.tvError.text = state.message
                    binding.tvError.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSetup.isEnabled = !loading
        binding.tvError.visibility = View.GONE
    }
}
