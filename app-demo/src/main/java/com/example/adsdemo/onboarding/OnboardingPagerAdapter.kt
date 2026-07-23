package com.example.adsdemo.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class OnboardingPagerAdapter(
    activity: FragmentActivity,
    private var pages: List<Int>,
) : FragmentStateAdapter(activity) {
    fun submitPages(newPages: List<Int>) {
        if (pages == newPages) return
        pages = newPages
        notifyDataSetChanged()
    }

    fun pages(): List<Int> = pages

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment =
        OnboardingFragment.newInstance(pages[position])

    override fun getItemId(position: Int): Long = pages[position].toLong()

    override fun containsItem(itemId: Long): Boolean = pages.any { it.toLong() == itemId }
}
