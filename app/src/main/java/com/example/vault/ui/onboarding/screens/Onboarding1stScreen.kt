package com.example.vault.ui.onboarding.screens

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.viewpager2.widget.ViewPager2
import com.example.vault.R
import com.example.vault.ui.onboarding.ViewPagerAdapter

class Onboarding1stScreen : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        val view = inflater.inflate(R.layout.fragment_onboarding1st_screen, container, false)

        val viewPager = activity?.findViewById<ViewPager2>(R.id.viewPager)

        view.findViewById<Button>(R.id.nextBtn1).setOnClickListener{
            viewPager?.currentItem = 1
        }
TODO("IMPLEMENT THE CUSTOM VIEWS (onboarding_layout_view and onboarding_videointro_view) IN ALL THE ONBOARDING SCREENS")

        return view
    }
}