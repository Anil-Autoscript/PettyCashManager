package com.pettycash.manager.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.bumptech.glide.Glide
import com.pettycash.manager.R
import com.pettycash.manager.data.local.PreferenceManager
import com.pettycash.manager.data.repository.TransactionRepository
import com.pettycash.manager.databinding.ActivityMainBinding
import com.pettycash.manager.ui.login.LoginActivity
import com.pettycash.manager.utils.NetworkUtils
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var prefs: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getInstance(this)

        setupNavigation()
        setupHeader()
        syncData()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        // FAB for quick add
        binding.fabAdd.setOnClickListener {
            navController.navigate(R.id.addTransactionFragment)
        }

        // Hide FAB on add screen
        navController.addOnDestinationChangedListener { _, dest, _ ->
            binding.fabAdd.visibility = if (dest.id == R.id.addTransactionFragment) View.GONE else View.VISIBLE
        }
    }

    private fun setupHeader() {
        binding.tvCompanyName.text = prefs.companyName
        binding.tvUsername.text = "Hello, ${prefs.username}"

        if (prefs.logoUrl.isNotEmpty()) {
            Glide.with(this)
                .load(prefs.logoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_company_placeholder)
                .into(binding.imgCompanyLogo)
        }

        // Sync status
        if (NetworkUtils.isConnected(this)) {
            binding.ivSyncStatus.setImageResource(R.drawable.ic_sync_ok)
        } else {
            binding.ivSyncStatus.setImageResource(R.drawable.ic_sync_offline)
        }
    }

    private fun syncData() {
        if (NetworkUtils.isConnected(this)) {
            lifecycleScope.launch {
                val repo = TransactionRepository(this@MainActivity)
                repo.syncFromServer(prefs.companyId)
                repo.syncUnsyncedTransactions(prefs.companyId)
            }
        }
    }

    fun logout() {
        prefs.clearSession()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
