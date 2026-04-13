package com.pettycash.manager.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.pettycash.manager.R
import com.pettycash.manager.data.local.PreferenceManager
import com.pettycash.manager.data.model.Transaction
import com.pettycash.manager.data.model.TransactionType
import com.pettycash.manager.data.repository.TransactionRepository
import com.pettycash.manager.databinding.FragmentReportsBinding
import com.pettycash.manager.ui.dashboard.RecentTransactionAdapter
import com.pettycash.manager.utils.CurrencyUtils
import com.pettycash.manager.utils.DateUtils
import com.pettycash.manager.utils.ExcelExporter
import kotlinx.coroutines.launch

// ─── ViewModel ────────────────────────────────────────────────────────────────
class ReportsViewModel(
    private val repo: TransactionRepository,
    private val companyId: String
) : ViewModel() {

    val allTransactions = repo.getTransactionsLive(companyId)

    private val _categories = androidx.lifecycle.MutableLiveData<List<String>>()
    val categories: androidx.lifecycle.LiveData<List<String>> = _categories

    fun loadCategories() {
        viewModelScope.launch {
            _categories.value = listOf("All") + repo.getExpenseCategories(companyId)
        }
    }

    fun getFilteredTransactions(
        allTx: List<Transaction>,
        typeFilter: String,
        categoryFilter: String,
        startDate: String,
        endDate: String
    ): List<Transaction> {
        return allTx.filter { tx ->
            val typeMatch = when (typeFilter) {
                "Expense" -> tx.type == TransactionType.EXPENSE
                "Cash In" -> tx.type == TransactionType.CASH_IN
                else -> true
            }
            val catMatch = categoryFilter == "All" || tx.category == categoryFilter
            val dateMatch = tx.date >= startDate && tx.date <= endDate
            typeMatch && catMatch && dateMatch
        }
    }
}

class ReportsViewModelFactory(
    private val repo: TransactionRepository,
    private val companyId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ReportsViewModel(repo, companyId) as T
    }
}

// ─── Fragment ─────────────────────────────────────────────────────────────────
class ReportsFragment : Fragment() {

    private var _binding: FragmentReportsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: PreferenceManager
    private lateinit var adapter: RecentTransactionAdapter

    private var startDate = DateUtils.getFirstDayOfMonth()
    private var endDate = DateUtils.getLastDayOfMonth()
    private var selectedType = "All"
    private var selectedCategory = "All"
    private var allTransactionsList = listOf<Transaction>()

    private val viewModel: ReportsViewModel by viewModels {
        ReportsViewModelFactory(
            TransactionRepository(requireContext()),
            PreferenceManager.getInstance(requireContext()).companyId
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReportsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager.getInstance(requireContext())

        setupRecyclerView()
        setupTypeFilter()
        setupDateRange()
        setupObservers()
        setupListeners()
        viewModel.loadCategories()
    }

    private fun setupRecyclerView() {
        adapter = RecentTransactionAdapter {}
        binding.rvTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = this@ReportsFragment.adapter
        }
    }

    private fun setupTypeFilter() {
        val typeOptions = listOf("All", "Expense", "Cash In")
        val typeAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, typeOptions)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerType.adapter = typeAdapter
        binding.spinnerType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedType = typeOptions[pos]
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupDateRange() {
        binding.tvDateRange.text = "${DateUtils.formatForDisplay(startDate)} – ${DateUtils.formatForDisplay(endDate)}"

        binding.btnDateRange.setOnClickListener {
            showDateRangePicker()
        }
    }

    private fun showDateRangePicker() {
        val cal = java.util.Calendar.getInstance()
        android.app.DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                startDate = "%04d-%02d-%02d".format(year, month + 1, day)
                android.app.DatePickerDialog(
                    requireContext(),
                    { _, y2, m2, d2 ->
                        endDate = "%04d-%02d-%02d".format(y2, m2 + 1, d2)
                        binding.tvDateRange.text = "${DateUtils.formatForDisplay(startDate)} – ${DateUtils.formatForDisplay(endDate)}"
                        applyFilters()
                    },
                    cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH),
                    cal.get(java.util.Calendar.DAY_OF_MONTH)
                ).show()
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun setupObservers() {
        viewModel.allTransactions.observe(viewLifecycleOwner) { transactions ->
            allTransactionsList = transactions
            applyFilters()
        }

        viewModel.categories.observe(viewLifecycleOwner) { categories ->
            val catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, categories)
            catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerCategory.adapter = catAdapter
            binding.spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    selectedCategory = categories[pos]
                    applyFilters()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
    }

    private fun setupListeners() {
        binding.btnExport.setOnClickListener {
            try {
                val file = ExcelExporter.exportTransactions(
                    context = requireContext(),
                    transactions = allTransactionsList,
                    companyName = prefs.companyName,
                    startDate = startDate,
                    endDate = endDate
                )
                ExcelExporter.shareExcelFile(requireContext(), file)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Export failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun applyFilters() {
        val filtered = viewModel.getFilteredTransactions(
            allTx = allTransactionsList,
            typeFilter = selectedType,
            categoryFilter = selectedCategory,
            startDate = startDate,
            endDate = endDate
        )
        adapter.submitList(filtered)

        val totalIn = filtered.filter { it.type == TransactionType.CASH_IN }.sumOf { it.amount }
        val totalOut = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        binding.tvFilteredIn.text = CurrencyUtils.format(totalIn)
        binding.tvFilteredOut.text = CurrencyUtils.format(totalOut)
        binding.tvFilteredBalance.text = CurrencyUtils.format(totalIn - totalOut)
        binding.tvCount.text = "${filtered.size} transactions"
        binding.tvNoData.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
