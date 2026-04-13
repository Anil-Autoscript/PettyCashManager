package com.pettycash.manager.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pettycash.manager.data.local.PreferenceManager
import com.pettycash.manager.databinding.ActivitySplashBinding
import com.pettycash.manager.ui.login.LoginActivity
import com.pettycash.manager.ui.main.MainActivity
import com.pettycash.manager.ui.setup.SetupActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var prefs: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getInstance(this)

        lifecycleScope.launch {
            delay(1800)
            navigateNext()
        }
    }

    private fun navigateNext() {
        val intent = when {
            !prefs.isCompanySetup -> Intent(this, SetupActivity::class.java)
            !prefs.isLoggedIn -> Intent(this, LoginActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }
        startActivity(intent)
        finish()
    }
}
