package com.example.adsmodule.debug.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsmodule.core.debug.AdsDebugApi
import com.example.adsmodule.core.debug.AdsDebugApiProvider
import com.example.adsmodule.debug.R
import com.example.adsmodule.debug.databinding.FragmentDebugInspectorBinding
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

abstract class BaseDebugInspectorFragment : Fragment() {
    private var _binding: FragmentDebugInspectorBinding? = null
    protected val binding get() = _binding!!
    protected val api: AdsDebugApi get() = AdsDebugApiProvider.get()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDebugInspectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupActions(binding.actionsContainer)
        render()
        observe()
    }

    protected open fun setupActions(container: LinearLayout) = Unit

    protected open fun observe() = Unit

    protected abstract fun render()

    protected fun setText(value: CharSequence) {
        binding.inspectorText.text = value
    }

    protected fun addAction(container: LinearLayout, label: String, onClick: () -> Unit) {
        container.addView(
            MaterialButton(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).also { it.topMargin = 8 }
                minHeight = (48 * resources.displayMetrics.density).toInt()
                text = label
                setOnClickListener { onClick() }
            },
        )
    }

    protected fun <T> collectWhileStarted(flow: Flow<T>, block: (T) -> Unit): Job =
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                flow.collect(block)
            }
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
