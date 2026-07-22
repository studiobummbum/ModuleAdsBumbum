package com.example.adsdemo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.adsdemo.databinding.ActivityMainBinding
import com.example.adsmodule.core.AdsCoreStatus
import com.example.adsmodule.debug.DebugDashboardActivity
import com.example.adsmodule.fake.FakeAdsSdkModule

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.projectStatusText.text = getString(
            R.string.project_status_value,
            AdsCoreStatus.description,
            FakeAdsSdkModule.status,
        )
        binding.openDebugDashboardButton.setOnClickListener {
            startActivity(Intent(this, DebugDashboardActivity::class.java))
        }
    }
}
