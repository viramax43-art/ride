package com.rideminiapp.ui.driver

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
class DriverCabinetFragment : Fragment() {
    @Inject lateinit var rideRepository: RideRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll = context.screenRoot()
        val root = context.sectionContainer()
        root.addView(context.titleView("Driver cabinet"))
        val status = TextView(context)
        root.addView(status)

        root.addView(context.primaryButton("Load session") {
            lifecycleScope.launch {
                runCatching { rideRepository.driverSession() }
                    .onSuccess {
                        status.text = "Driver: ${it.name}\nRating: ${it.rating}\nSell points: ${it.canSellPoints}"
                    }
                    .onFailure {
                        status.text = it.message ?: "Failed to load driver session."
                    }
            }
        })
        root.addView(context.primaryButton("Load cabinet") {
            lifecycleScope.launch {
                runCatching { rideRepository.pricing() }
                    .onSuccess {
                        status.text = "Pricing mode: ${it.pricingMode}\nPoints/ride: ${it.pointsPerRide}"
                    }
                    .onFailure {
                        status.text = it.message ?: "Failed to load cabinet."
                    }
            }
        })
        scroll.addView(root)
        return scroll
    }
}
