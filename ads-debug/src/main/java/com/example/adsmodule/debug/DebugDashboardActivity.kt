package com.example.adsmodule.debug

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.adsmodule.debug.databinding.ActivityDebugDashboardBinding

class DebugDashboardActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDebugDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDebugDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.closeButton.setOnClickListener {
            finish()
        }
    }
}
