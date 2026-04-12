package com.example.smartlightswitch

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smartlightswitch.databinding.ActivityLoginBinding
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.api.IRegisterCallback
import com.thingclips.smart.android.user.bean.User
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IResultCallback

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ThingHomeSdk.getUserInstance().isLogin) {
            navigateToHome()
            return
        }

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.btnSendCode.setOnClickListener { sendVerificationCode() }
        binding.btnRegister.setOnClickListener { completeRegistration() }
    }

    private fun attemptLogin() {
        val countryCode = binding.etCountryCode.text.toString().trim().trimStart('+')
        val email       = binding.etEmail.text.toString().trim()
        val password    = binding.etPassword.text.toString()

        if (countryCode.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        ThingHomeSdk.getUserInstance().loginWithEmail(
            countryCode, email, password,
            object : ILoginCallback {
                override fun onSuccess(user: User?) {
                    runOnUiThread { setLoading(false); navigateToHome() }
                }
                override fun onError(code: String?, error: String?) {
                    runOnUiThread {
                        setLoading(false)
                        Toast.makeText(this@LoginActivity, "Login failed: $error (code: $code)", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun sendVerificationCode() {
        val countryCode = binding.etCountryCode.text.toString().trim().trimStart('+')
        val email       = binding.etEmail.text.toString().trim()

        if (countryCode.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Enter country code and email first", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        ThingHomeSdk.getUserInstance().getRegisterEmailValidateCode(
            countryCode, email,
            object : IResultCallback {
                override fun onSuccess() {
                    runOnUiThread {
                        setLoading(false)
                        binding.tilCode.visibility = View.VISIBLE
                        binding.btnRegister.visibility = View.VISIBLE
                        Toast.makeText(this@LoginActivity, "Code sent to $email", Toast.LENGTH_LONG).show()
                    }
                }
                override fun onError(code: String?, error: String?) {
                    runOnUiThread {
                        setLoading(false)
                        Toast.makeText(this@LoginActivity, "Failed to send code: $error", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun completeRegistration() {
        val countryCode = binding.etCountryCode.text.toString().trim().trimStart('+')
        val email       = binding.etEmail.text.toString().trim()
        val password    = binding.etPassword.text.toString()
        val code        = binding.etCode.text.toString().trim()

        if (code.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter the verification code and a password", Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        ThingHomeSdk.getUserInstance().registerAccountWithEmail(
            countryCode, email, password, code,
            object : IRegisterCallback {
                override fun onSuccess(user: User?) {
                    runOnUiThread { setLoading(false); navigateToHome() }
                }
                override fun onError(code: String?, error: String?) {
                    runOnUiThread {
                        setLoading(false)
                        Toast.makeText(this@LoginActivity, "Registration failed: $error (code: $code)", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
        binding.btnSendCode.isEnabled = !loading
        binding.btnRegister.isEnabled = !loading
    }
}

