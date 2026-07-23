package com.example.adsmodule.debug.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.adsmodule.debug.R
import com.example.adsmodule.debug.databinding.FragmentNativeLayoutGalleryBinding

class NativeLayoutGalleryFragment : Fragment() {
    private var _binding: FragmentNativeLayoutGalleryBinding? = null
    private val binding get() = _binding!!

    private var galleryState: GalleryState = GalleryState.READY
    private var longText: Boolean = false
    private var missingMedia: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentNativeLayoutGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.stateReadyButton.setOnClickListener {
            galleryState = GalleryState.READY
            bindAll()
        }
        binding.stateLoadingButton.setOnClickListener {
            galleryState = GalleryState.LOADING
            bindAll()
        }
        binding.stateErrorButton.setOnClickListener {
            galleryState = GalleryState.ERROR
            bindAll()
        }
        binding.stateEmptyButton.setOnClickListener {
            galleryState = GalleryState.EMPTY
            bindAll()
        }
        binding.toggleLongTextButton.setOnClickListener {
            longText = !longText
            bindAll()
        }
        binding.toggleMissingMediaButton.setOnClickListener {
            missingMedia = !missingMedia
            bindAll()
        }
        binding.toggleDarkButton.setOnClickListener {
            val night = AppCompatDelegate.getDefaultNightMode()
            AppCompatDelegate.setDefaultNightMode(
                if (night == AppCompatDelegate.MODE_NIGHT_YES) {
                    AppCompatDelegate.MODE_NIGHT_NO
                } else {
                    AppCompatDelegate.MODE_NIGHT_YES
                },
            )
        }
        inflateTemplates()
        bindAll()
    }

    private fun inflateTemplates() {
        val inflater = layoutInflater
        binding.gallerySmallContainer.removeAllViews()
        binding.galleryMediumContainer.removeAllViews()
        binding.galleryFullContainer.removeAllViews()
        inflater.inflate(R.layout.view_gallery_native_small, binding.gallerySmallContainer, true)
        inflater.inflate(R.layout.view_gallery_native_medium, binding.galleryMediumContainer, true)
        inflater.inflate(R.layout.view_gallery_native_full, binding.galleryFullContainer, true)
    }

    private fun bindAll() {
        bindCard(binding.gallerySmallContainer, size = "small")
        bindCard(binding.galleryMediumContainer, size = "medium")
        bindCard(binding.galleryFullContainer, size = "full")
    }

    private fun bindCard(container: FrameLayout, size: String) {
        val root = container.getChildAt(0) ?: return
        val content = root.findViewById<View>(R.id.native_content)
        val stateBanner = root.findViewById<TextView>(R.id.state_banner)
        val overlay = root.findViewById<TextView>(R.id.debug_overlay)
        val title = root.findViewById<TextView>(R.id.native_title)
        val body = root.findViewById<TextView>(R.id.native_body)
        val media = root.findViewById<View?>(R.id.native_media)
        val icon = root.findViewById<View?>(R.id.native_icon)

        overlay.text = "cfg=gallery size=$size state=$galleryState"
        when (galleryState) {
            GalleryState.READY -> {
                content.isVisible = true
                stateBanner.isVisible = false
                title?.text = if (longText) {
                    getString(R.string.debug_long_title)
                } else {
                    "Native $size"
                }
                body?.text = if (longText) {
                    getString(R.string.debug_long_body)
                } else {
                    "Ready creative body"
                }
                media?.isVisible = !missingMedia
                icon?.isVisible = !missingMedia
            }
            GalleryState.LOADING -> {
                content.isVisible = false
                stateBanner.isVisible = true
                stateBanner.text = getString(R.string.debug_loading)
            }
            GalleryState.ERROR -> {
                content.isVisible = false
                stateBanner.isVisible = true
                stateBanner.text = getString(R.string.debug_error)
            }
            GalleryState.EMPTY -> {
                content.isVisible = false
                stateBanner.isVisible = true
                stateBanner.text = getString(R.string.debug_empty)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private enum class GalleryState {
        READY,
        LOADING,
        ERROR,
        EMPTY,
    }
}
