package com.example.vault.ui.onboarding

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager2.widget.ViewPager2
import com.example.vault.R
import com.example.vault.ui.onboarding.screens.Onboarding1stScreen
import com.example.vault.ui.onboarding.screens.Onboarding2ndScreen
import com.example.vault.ui.onboarding.screens.Onboarding3rdScreen

class ViewPagerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_view_pager, container, false)




        // fragment list to handle onboarding navigation
        val fragmentList = arrayListOf<Fragment>(
            Onboarding1stScreen(),
            Onboarding2ndScreen(),
            Onboarding3rdScreen()
        )

        val adapter = ViewPagerAdapter(
            fragmentList,
            requireActivity().supportFragmentManager,
            lifecycle
        )

        view.findViewById<ViewPager2>(R.id.viewPager).adapter = adapter



        return view
    }
}