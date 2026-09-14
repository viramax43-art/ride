package com.rideminiapp.ui.passenger

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.data.repository.RideRepository
import com.rideminiapp.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PassengerProfileFragment : Fragment() {
    @Inject lateinit var rideRepository: RideRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll = context.screenRoot()
        val root = context.sectionContainer()
        root.addView(context.titleView("Profile"))
        val cabinet = TextView(context)
        root.addView(cabinet)
        root.addView(context.primaryButton("Refresh") {
            lifecycleScope.launch {
                runCatching { rideRepository.userCabinet() }
                    .onSuccess {
                        cabinet.text = "User: ${it.username}\nPoints: ${it.pointsBalance}\nRating: ${it.rating}"
                    }
                    .onFailure {
                        cabinet.text = it.message ?: "Failed to load profile."
                    }
            }
        })
        scroll.addView(root)
        return scroll
    }
}
