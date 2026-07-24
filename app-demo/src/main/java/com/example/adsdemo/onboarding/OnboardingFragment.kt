package com.example.adsdemo.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.R
import com.example.adsdemo.databinding.FragmentOnboardingBinding
import com.example.adsdemo.language.NormalNativeAdBinder
import com.example.adsdemo.sdk.SelectingNormalNativeAdBinder
import com.example.adsmodule.core.normal.NormalScreenBindResult
import com.example.adsmodule.core.normal.NormalScreenUnbindMode
import com.example.adsmodule.core.storage.OnboardingScreenInstances
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class OnboardingFragment : Fragment() {
    private var binding: FragmentOnboardingBinding? = null
    private val binder: NormalNativeAdBinder by lazy {
        SelectingNormalNativeAdBinder(
            (requireContext().applicationContext as AdsDemoApplication).graph.sdkBackend,
        )
    }
    private var boundLogicalPage: Int? = null

    private val logicalPage: Int
        get() = requireArguments().getInt(ARG_LOGICAL_PAGE)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val created = FragmentOnboardingBinding.inflate(inflater, container, false)
        binding = created
        return created.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val page = logicalPage
        val binding = binding ?: return
        binding.onboardingPageTitle.text = getString(R.string.onboarding_page_title, page)
        binding.onboardingPageBody.text = getString(
            R.string.onboarding_page_body,
            OnboardingScreenInstances.page(page).value,
        )

        val graph = (requireActivity().application as AdsDemoApplication).graph
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                graph.onboardingAds.onPageVisible(page)
                when (val result = graph.onboardingAds.bindPage(page)) {
                    is NormalScreenBindResult.Bound -> {
                        boundLogicalPage = page
                        binder.bindNative(
                            container = binding.onboardingNativeContainer,
                            storedAd = result.session.storedAd,
                            title = getString(R.string.onboarding_native_title, page),
                        )
                    }
                    is NormalScreenBindResult.Rejected -> {
                        binder.clear(binding.onboardingNativeContainer)
                    }
                }
                try {
                    awaitCancellation()
                } finally {
                    releaseAd(page)
                }
            }
        }
    }

    private fun releaseAd(page: Int) {
        val container = binding?.onboardingNativeContainer
        if (container != null) {
            binder.clear(container)
        }
        if (boundLogicalPage == page) {
            val graph = (requireActivity().application as AdsDemoApplication).graph
            graph.onboardingAds.unbindPage(page, NormalScreenUnbindMode.RELEASE)
            boundLogicalPage = null
        }
    }

    override fun onDestroyView() {
        val page = runCatching { logicalPage }.getOrNull()
        if (page != null && boundLogicalPage == page) {
            val app = activity?.application as? AdsDemoApplication
            binding?.onboardingNativeContainer?.let { binder.clear(it) }
            app?.graph?.onboardingAds?.unbindPage(page, NormalScreenUnbindMode.RELEASE)
            boundLogicalPage = null
        }
        binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_LOGICAL_PAGE = "logical_page"

        fun newInstance(logicalPage: Int): OnboardingFragment =
            OnboardingFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_LOGICAL_PAGE, logicalPage)
                }
            }
    }
}
