package com.pettycash.manager.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pettycash.manager.R
import com.pettycash.manager.data.local.PreferenceManager
import com.pettycash.manager.databinding.FragmentProfileBinding
import com.pettycash.manager.ui.main.MainActivity
import com.pettycash.manager.utils.DateUtils

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: PreferenceManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager.getInstance(requireContext())

        populateProfile()
        setupListeners()
    }

    private fun populateProfile() {
        binding.tvUsername.text = prefs.username
        binding.tvRole.text = prefs.userRole.replaceFirstChar { it.uppercase() }
        binding.tvCompany.text = prefs.companyName
        binding.tvCompanyId.text = "ID: ${prefs.companyId}"

        val lastSync = prefs.lastSyncTime
        binding.tvLastSync.text = if (lastSync > 0)
            "Last synced: ${java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastSync))}"
        else "Not synced yet"

        if (prefs.logoUrl.isNotEmpty()) {
            Glide.with(this).load(prefs.logoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_company_placeholder)
                .into(binding.imgLogo)
        }

        // Approval toggle - admin only
        binding.cardApproval.visibility = if (prefs.isAdmin) View.VISIBLE else View.GONE
        binding.switchApproval.isChecked = prefs.isApprovalEnabled
        binding.switchApproval.setOnCheckedChangeListener { _, isChecked ->
            prefs.isApprovalEnabled = isChecked
        }

        // Admin-only section
        binding.cardAdminSection.visibility = if (prefs.isAdmin) View.VISIBLE else View.GONE
    }

    private fun setupListeners() {
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout") { _, _ ->
                    (activity as? MainActivity)?.logout()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        binding.btnManageUsers.setOnClickListener {
            // Navigate to user management (admin only)
            com.google.android.material.snackbar.Snackbar
                .make(binding.root, "User Management — Coming in next version", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun showChangePasswordDialog() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_change_password, null)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                // Handle password change
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
