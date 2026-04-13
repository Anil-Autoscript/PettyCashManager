package com.pettycash.manager.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.pettycash.manager.R
import com.pettycash.manager.data.local.PreferenceManager
import com.pettycash.manager.data.model.DashboardSummary
import com.pettycash.manager.data.model.Transaction
import com.pettycash.manager.data.model.TransactionType
import com.pettycash.manager.data.repository.TransactionRepository
import com.pettycash.manager.databinding.FragmentDashboardBinding
import com.pettycash.manager.utils.CurrencyUtils
import com.pettycash.manager.utils.Result
import kotlinx.coroutines.launch

// ─── ViewModel ────────────────────────────────────────────────────────────────
class DashboardViewModel(private val repo: TransactionRepository, private val companyId: String) : ViewModel() {

    private val _summary = androidx.lifecycle.MutableLiveData<DashboardSummary>()
    val summary: androidx.lifecycle.LiveData<DashboardSummary> = _summary

    private val _recentTransactions = androidx.lifecycle.MutableLiveData<List<Transaction>>()
    val recentTransactions: androidx.lifecycle.LiveData<List<Transaction>> = _recentTransactions

    val allTransactions = repo.getTransactionsLive(companyId)

    fun loadDashboard() {
        viewModelScope.launch {
            when (val result = repo.getDashboardSummary(companyId)) {
                is Result.Success -> _summary.value = result.data
                else -> {}
            }
        }
    }
}

class DashboardViewModelFactory(
    private val repo: TransactionRepository,
    private val companyId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DashboardViewModel(repo, companyId) as T
    }
}

// ─── Fragment ─────────────────────────────────────────────────────────────────
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PreferenceManager
    private lateinit var transactionAdapter: RecentTransactionAdapter

    private val viewModel: DashboardViewModel by viewModels {
        DashboardViewModelFactory(
            TransactionRepository(requireContext()),
            PreferenceManager.getInstance(requireContext()).companyId
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager.getInstance(requireContext())

        setupRecyclerView()
        setupObservers()
        setupListeners()
        viewModel.loadDashboard()
    }

    private fun setupRecyclerView() {
        transactionAdapter = RecentTransactionAdapter { tx ->
            // Navigate to transaction detail
        }
        binding.rvRecentTransactions.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = transactionAdapter
        }
    }

    private fun setupObservers() {
        viewModel.summary.observe(viewLifecycleOwner) { summary ->
            binding.tvBalance.text = CurrencyUtils.format(summary.currentBalance)
            binding.tvTotalIn.text = CurrencyUtils.format(summary.totalCashIn)
            binding.tvTotalOut.text = CurrencyUtils.format(summary.totalExpense)
            binding.tvPendingCount.text = summary.pendingApprovals.toString()
            binding.cardPending.visibility =
                if (prefs.isApprovalEnabled && summary.pendingApprovals > 0) View.VISIBLE else View.GONE
        }

        viewModel.allTransactions.observe(viewLifecycleOwner) { transactions ->
            val recent = transactions.take(5)
            transactionAdapter.submitList(recent)
            binding.tvNoTransactions.visibility = if (recent.isEmpty()) View.VISIBLE else View.GONE
            viewModel.loadDashboard()
        }
    }

    private fun setupListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadDashboard()
            binding.swipeRefresh.isRefreshing = false
        }

        binding.btnViewAll.setOnClickListener {
            findNavController().navigate(R.id.reportsFragment)
        }

        binding.cardAddExpense.setOnClickListener {
            findNavController().navigate(R.id.addTransactionFragment)
        }

        binding.cardAddCashIn.setOnClickListener {
            val bundle = Bundle().apply { putString("type", "CASH_IN") }
            findNavController().navigate(R.id.addTransactionFragment, bundle)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

// ─── Recent Transaction Adapter ───────────────────────────────────────────────
class RecentTransactionAdapter(
    private val onItemClick: (Transaction) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Transaction,
        RecentTransactionAdapter.ViewHolder>(TransactionDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val tvCategory = itemView.findViewById<android.widget.TextView>(R.id.tvCategory)
        private val tvDescription = itemView.findViewById<android.widget.TextView>(R.id.tvDescription)
        private val tvAmount = itemView.findViewById<android.widget.TextView>(R.id.tvAmount)
        private val tvDate = itemView.findViewById<android.widget.TextView>(R.id.tvDate)
        private val ivType = itemView.findViewById<android.widget.ImageView>(R.id.ivType)

        fun bind(tx: Transaction) {
            tvCategory.text = tx.category
            tvDescription.text = tx.description.ifEmpty { "—" }
            tvDate.text = com.pettycash.manager.utils.DateUtils.formatForDisplay(tx.date)

            if (tx.type == TransactionType.CASH_IN) {
                tvAmount.text = "+ ${CurrencyUtils.format(tx.amount)}"
                tvAmount.setTextColor(itemView.context.getColor(R.color.green_500))
                ivType.setImageResource(R.drawable.ic_cash_in)
                ivType.setColorFilter(itemView.context.getColor(R.color.green_500))
            } else {
                tvAmount.text = "- ${CurrencyUtils.format(tx.amount)}"
                tvAmount.setTextColor(itemView.context.getColor(R.color.red_500))
                ivType.setImageResource(R.drawable.ic_expense)
                ivType.setColorFilter(itemView.context.getColor(R.color.red_500))
            }

            itemView.setOnClickListener { onItemClick(tx) }
        }
    }

    class TransactionDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Transaction>() {
        override fun areItemsTheSame(old: Transaction, new: Transaction) = old.transactionId == new.transactionId
        override fun areContentsTheSame(old: Transaction, new: Transaction) = old == new
    }
}
