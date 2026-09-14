package com.rideminiapp.ui.passenger

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.rideminiapp.ui.common.SharedMapFragment
import com.rideminiapp.ui.common.replaceChild
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PassengerShellFragment : Fragment() {
    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val menu = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val containerId = View.generateViewId()
        root.addView(menu)
        root.addView(androidx.fragment.app.FragmentContainerView(context).apply {
            id = containerId
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        })

        fun addTab(label: String, fragmentFactory: () -> Fragment) {
            menu.addView(Button(context).apply {
                text = label
                setOnClickListener { replaceChild(containerId, fragmentFactory()) }
            })
        }

        addTab("Request") { PassengerRequestFragment() }
        addTab("Trips") { PassengerRequestsFragment() }
        addTab("Profile") { PassengerProfileFragment() }
        addTab("Tools") { PassengerToolsFragment() }
        addTab("Map") { SharedMapFragment() }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(containerId, SharedMapFragment())
                .commitNowAllowingStateLoss()
        }
        return root
    }
}
