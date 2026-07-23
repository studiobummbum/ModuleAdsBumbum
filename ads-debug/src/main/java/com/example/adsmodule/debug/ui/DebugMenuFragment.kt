package com.example.adsmodule.debug.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsmodule.core.debug.AdsDebugApi
import com.example.adsmodule.core.debug.AdsDebugApiProvider
import com.example.adsmodule.debug.AdsDebugDashboardActivity
import com.example.adsmodule.debug.DebugDestination
import com.example.adsmodule.debug.databinding.FragmentDebugMenuBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

open class DebugMenuFragment : Fragment() {
    private var _binding: FragmentDebugMenuBinding? = null
    private val binding get() = _binding!!
    private val api: AdsDebugApi get() = AdsDebugApiProvider.get()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDebugMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = activity as AdsDebugDashboardActivity
        DebugDestination.entries
            .filter { it != DebugDestination.DASHBOARD }
            .forEach { destination ->
                val button = MaterialButton(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).also { it.topMargin = 8 }
                    minHeight = (48 * resources.displayMetrics.density).toInt()
                    text = destination.title
                    setOnClickListener { host.openDestination(destination) }
                }
                binding.destinationList.addView(button)
            }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                api.dashboard.collect { snap ->
                    binding.dashboardSnapshotText.text = buildString {
                        appendLine("session=${snap.sessionId?.value}")
                        appendLine("configVersion=${snap.configVersion}")
                        appendLine("configHash=${snap.configContentHash?.take(12)}")
                        appendLine(
                            "nav=${snap.navigation.activityName}/" +
                                "${snap.navigation.fragmentName}/pager=${snap.navigation.pagerIndex}",
                        )
                        appendLine("fullscreenOwner=${snap.fullscreenLock.owner?.showRequestId?.value}")
                        appendLine("clickTokens=${snap.clickTokens.tokens.size}")
                        appendLine("ready=${snap.readyObjectCount}")
                        appendLine("refillInFlight=${snap.refillInFlightCount}")
                        appendLine("activeLoads=${snap.activeLoadCycleCount}")
                        appendLine(
                            "latest=${snap.latestEvent?.let { "${it.category}: ${it.message}" }}",
                        )
                    }
                    binding.dashboardSnapshotText.isVisible = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Home dashboard content alias used by destination enum. */
class DashboardHomeFragment : DebugMenuFragment()
