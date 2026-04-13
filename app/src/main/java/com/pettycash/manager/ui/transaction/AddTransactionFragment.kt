package com.pettycash.manager.ui.transaction

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.pettycash.manager.R
import com.pettycash.manager.data.local.PreferenceManager
import com.pettycash.manager.data.model.ApprovalStatus
import com.pettycash.manager.data.model.Transaction
import com.pettycash.manager.data.model.TransactionType
import com.pettycash.manager.data.repository.TransactionRepository
import com.pettycash.manager.databinding.FragmentAddTransactionBinding
import com.pettycash.manager.utils.DateUtils
import com.pettycash.manager.utils.ImageUtils
import com.pettycash.manager.utils.Result
import kotlinx.coroutines.launch
import java.util.*

// ─── ViewModel ────────────────────────────────────────────────────────────────
class AddTransactionViewModel(
    private val repo: TransactionRepository,
    private val companyId: String
) : ViewModel() {

    private val _state = androidx.lifecycle.MutableLiveData<AddTxState>()
    val state: androidx.lifecycle.LiveData<AddTxState> = _state

    private val _categories = androidx.lifecycle.MutableLiveData<List<String>>()
    val categories: androidx.lifecycle.LiveData<List<String>> = _categories

    private val _uploadedImageUrl = androidx.lifecycle.MutableLiveData<String>()
    val uploadedImageUrl: androidx.lifecycle.LiveData<String> = _uploadedImageUrl

    fun loadCategories() {
        viewModelScope.launch {
            _categories.value = repo.getExpenseCategories(companyId)
        }
    }

    fun uploadImage(imageBase64: String, fileName: String) {
        viewModelScope.launch {
            _state.value = AddTxState.UploadingImage
            when (val result = repo.uploadBillImage(companyId, imageBase64, fileName)) {
                is Result.Success -> {
                    _uploadedImageUrl.value = result.data
                    _state.value = AddTxState.ImageUploaded
                }
                is Result.Error -> _state.value = AddTxState.Error("Image upload failed: ${result.message}")
                else -> {}
            }
        }
    }

    fun saveTransaction(
        type: TransactionType,
        amount: String,
        date: String,
        category: String,
        description: String,
        imageUrl: String,
        addedBy: String,
        approvalEnabled: Boolean
    ) {
        val amountDouble = amount.toDoubleOrNull()
        if (amountDouble == null || amountDouble <= 0) {
            _state.value = AddTxState.Error("Please enter a valid amount")
            return
        }
        if (category.isBlank()) {
            _state.value = AddTxState.Error("Please select a category")
            return
        }
        _state.value = AddTxState.Saving
        viewModelScope.launch {
            val approvalStatus = when {
                type == TransactionType.CASH_IN -> ApprovalStatus.NOT_REQUIRED
                approvalEnabled -> ApprovalStatus.PENDING
                else -> ApprovalStatus.NOT_REQUIRED
            }
            val tx = Transaction(
                transactionId = UUID.randomUUID().toString(),
                companyId = companyId,
                type = type,
                amount = amountDouble,
                date = date,
                category = category,
                description = description,
                billImageUrl = imageUrl,
                addedBy = addedBy,
                approvalStatus = approvalStatus
            )
            when (val result = repo.addTransaction(tx)) {
                is Result.Success -> _state.value = AddTxState.Success
                is Result.Error -> _state.value = AddTxState.Error(result.message)
                else -> {}
            }
        }
    }

    sealed class AddTxState {
        object Saving : AddTxState()
        object Success : AddTxState()
        object UploadingImage : AddTxState()
        object ImageUploaded : AddTxState()
        data class Error(val message: String) : AddTxState()
    }
}

class AddTransactionViewModelFactory(
    private val repo: TransactionRepository,
    private val companyId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AddTransactionViewModel(repo, companyId) as T
    }
}

// ─── Fragment ─────────────────────────────────────────────────────────────────
class AddTransactionFragment : Fragment() {

    private var _binding: FragmentAddTransactionBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PreferenceManager
    private var selectedDate = DateUtils.getCurrentDate()
    private var uploadedImageUrl = ""
    private var selectedImageUri: Uri? = null
    private var currentType = TransactionType.EXPENSE

    private val viewModel: AddTransactionViewModel by viewModels {
        AddTransactionViewModelFactory(
            TransactionRepository(requireContext()),
            PreferenceManager.getInstance(requireContext()).companyId
        )
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            Glide.with(this).load(it).centerCrop().into(binding.imgBill)
            binding.cardBillImage.visibility = View.VISIBLE
            binding.tvUploadBill.text = "Change Bill Photo"
            // Upload immediately
            val base64 = ImageUtils.uriToBase64(requireContext(), it)
            val fileName = "bill_${System.currentTimeMillis()}.jpg"
            viewModel.uploadImage(base64, fileName)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager.getInstance(requireContext())

        // Check if type pre-selected from bundle
        arguments?.getString("type")?.let {
            if (it == "CASH_IN") selectType(TransactionType.CASH_IN)
        }

        setupTypeToggle()
        setupDatePicker()
        setupListeners()
        setupObservers()
        viewModel.loadCategories()
        binding.etDate.setText(DateUtils.formatForDisplay(selectedDate))
    }

    private fun setupTypeToggle() {
        binding.btnExpense.setOnClickListener { selectType(TransactionType.EXPENSE) }
        binding.btnCashIn.setOnClickListener { selectType(TransactionType.CASH_IN) }
        selectType(currentType)
    }

    private fun selectType(type: TransactionType) {
        currentType = type
        if (type == TransactionType.EXPENSE) {
            binding.btnExpense.setBackgroundResource(R.drawable.bg_type_selected)
            binding.btnExpense.setTextColor(requireContext().getColor(R.color.white))
            binding.btnCashIn.setBackgroundResource(R.drawable.bg_type_unselected)
            binding.btnCashIn.setTextColor(requireContext().getColor(R.color.blue_600))
            binding.layoutCategory.visibility = View.VISIBLE
            binding.layoutBill.visibility = View.VISIBLE
        } else {
            binding.btnCashIn.setBackgroundResource(R.drawable.bg_type_selected)
            binding.btnCashIn.setTextColor(requireContext().getColor(R.color.white))
            binding.btnExpense.setBackgroundResource(R.drawable.bg_type_unselected)
            binding.btnExpense.setTextColor(requireContext().getColor(R.color.blue_600))
            binding.layoutCategory.visibility = View.GONE
            binding.layoutBill.visibility = View.GONE
        }
    }

    private fun setupDatePicker() {
        binding.etDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    selectedDate = "%04d-%02d-%02d".format(year, month + 1, day)
                    binding.etDate.setText(DateUtils.formatForDisplay(selectedDate))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun setupListeners() {
        binding.layoutBillUpload.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        binding.imgBillRemove.setOnClickListener {
            selectedImageUri = null
            uploadedImageUrl = ""
            binding.cardBillImage.visibility = View.GONE
            binding.tvUploadBill.text = "Upload Bill Photo"
        }

        binding.btnSave.setOnClickListener {
            val category = if (currentType == TransactionType.EXPENSE)
                binding.spinnerCategory.selectedItem?.toString() ?: ""
            else "Cash In"

            viewModel.saveTransaction(
                type = currentType,
                amount = binding.etAmount.text.toString(),
                date = selectedDate,
                category = category,
                description = binding.etDescription.text.toString().trim(),
                imageUrl = uploadedImageUrl,
                addedBy = prefs.username,
                approvalEnabled = prefs.isApprovalEnabled
            )
        }

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupObservers() {
        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                categories
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerCategory.adapter = adapter
        }

        viewModel.uploadedImageUrl.observe(viewLifecycleOwner) { url ->
            uploadedImageUrl = url
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AddTransactionViewModel.AddTxState.Saving -> showLoading(true)
                is AddTransactionViewModel.AddTxState.Success -> {
                    showLoading(false)
                    Snackbar.make(binding.root, "Transaction saved!", Snackbar.LENGTH_SHORT).show()
                    findNavController().navigateUp()
                }
                is AddTransactionViewModel.AddTxState.UploadingImage -> {
                    binding.tvUploadBill.text = "Uploading..."
                }
                is AddTransactionViewModel.AddTxState.ImageUploaded -> {
                    binding.tvUploadBill.text = "Bill Uploaded ✓"
                }
                is AddTransactionViewModel.AddTxState.Error -> {
                    showLoading(false)
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
