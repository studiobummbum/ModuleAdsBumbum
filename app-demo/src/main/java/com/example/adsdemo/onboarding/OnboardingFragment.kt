package com.example.adsdemo.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.adsdemo.AdsDemoApplication
import com.example.adsdemo.AdsDemoGraph
import com.example.adsdemo.R
import com.example.adsdemo.databinding.FragmentOnboardingBinding
import com.example.adsdemo.language.NormalNativeAdBinder
import com.example.adsdemo.sdk.AdMobNormalNativeAdBinder
import com.example.adsmodule.core.normal.NormalScreenBindResult
import com.example.adsmodule.core.normal.NormalScreenUnbindMode
import com.example.adsmodule.core.storage.OnboardingScreenInstances
import kotlinx.coroutines.launch

class OnboardingFragment : Fragment() {
    private var binding: FragmentOnboardingBinding? = null
    private val binder: NormalNativeAdBinder by lazy {
        AdMobNormalNativeAdBinder()
    }
    private var boundLogicalPage: Int? = null
    private var boundObjectId: String? = null

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
        binding.onboardingHero.setImageResource(heroForPage(page))
        binding.onboardingPageTitle.text = captionForPage(page)
        binding.onboardingPageBody.text = getString(
            R.string.onboarding_page_body,
            OnboardingScreenInstances.page(page).value,
        )
        val activePages = (activity as? OnboardingActivity)?.activePages().orEmpty()
            .ifEmpty { listOf(1, 2, 3, 4) }
        renderDots(binding, page, activePages)
        binding.onboardingNextLink.setOnClickListener {
            (activity as? OnboardingActivity)?.forwardFromFragment()
        }

        val graph = (requireActivity().application as AdsDemoApplication).graph
        // STARTED (not RESUMED): keep native bound across offscreen / pause without clear.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                graph.onboardingAds.onPageVisible(page)
                applyBindResult(graph, binding, page, graph.onboardingAds.bindPage(page))
                // Back/swipe-return reload: keep sticky UI, swap when a newer READY arrives.
                launch {
                    graph.onboardingAds.pageBindEvents.collect { event ->
                        if (event.logicalPage != page) return@collect
                        applyBindResult(graph, binding, page, event.result)
                    }
                }
            }
        }
    }

    private fun applyBindResult(
        graph: AdsDemoGraph,
        binding: FragmentOnboardingBinding,
        page: Int,
        result: NormalScreenBindResult,
    ) {
        when (result) {
            is NormalScreenBindResult.Bound -> {
                boundLogicalPage = page
                val objectId = result.session.objectId.value
                if (boundObjectId != objectId) {
                    binder.bindNative(
                        container = binding.onboardingNativeContainer,
                        storedAd = result.session.storedAd,
                        title = getString(R.string.onboarding_native_title, page),
                    )
                    boundObjectId = objectId
                }
                result.previousSession?.let { previous ->
                    graph.onboardingAds.consumeReplacedSession(previous)
                }
            }
            is NormalScreenBindResult.Rejected -> {
                // Sticky: keep any already-rendered native; only blank if never shown.
                if (binding.onboardingNativeContainer.childCount == 0) {
                    binder.clear(binding.onboardingNativeContainer)
                    boundObjectId = null
                }
            }
        }
    }

    private fun renderDots(
        binding: FragmentOnboardingBinding,
        activePage: Int,
        activePages: List<Int>,
    ) {
        binding.onboardingDots.removeAllViews()
        val size = resources.getDimensionPixelSize(R.dimen.onboarding_dot_size)
        val margin = resources.getDimensionPixelSize(R.dimen.onboarding_dot_margin)
        val total = activePages.size.coerceAtLeast(1)
        activePages.forEachIndexed { index, logicalPage ->
            val dot = ImageView(requireContext()).apply {
                layoutParams = ViewGroup.MarginLayoutParams(size, size).also {
                    it.marginEnd = margin
                }
                setImageDrawable(
                    ContextCompat.getDrawable(
                        requireContext(),
                        if (logicalPage == activePage) {
                            R.drawable.bg_onboard_dot_active
                        } else {
                            R.drawable.bg_onboard_dot_inactive
                        },
                    ),
                )
                contentDescription = getString(
                    R.string.onboarding_page_indicator,
                    index + 1,
                    total,
                )
            }
            binding.onboardingDots.addView(dot)
        }
    }

    private fun heroForPage(page: Int): Int = when (page) {
        1 -> R.drawable.img_onboard_1
        2 -> R.drawable.img_onboard_2
        3 -> R.drawable.img_onboard_3
        else -> R.drawable.img_onboard_4
    }

    private fun captionForPage(page: Int): String = when (page) {
        1 -> getString(R.string.onboarding_caption_1)
        2 -> getString(R.string.onboarding_caption_2)
        3 -> getString(R.string.onboarding_caption_3)
        else -> getString(R.string.onboarding_caption_4)
    }

    override fun onDestroyView() {
        val page = runCatching { logicalPage }.getOrNull()
        val container = binding?.onboardingNativeContainer
        if (page != null && boundLogicalPage == page) {
            val app = activity?.application as? AdsDemoApplication
            // Teardown only: clear UI then CONSUME so refill can prepare the next visit.
            container?.let { binder.clear(it) }
            app?.graph?.onboardingAds?.unbindPage(page, NormalScreenUnbindMode.CONSUME)
            boundLogicalPage = null
            boundObjectId = null
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
