package com.rideminiapp.ui.driver

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.data.repository.RideRepository
import com.rideminiapp.ui.common.bodyView
import com.rideminiapp.ui.common.primaryButton
import com.rideminiapp.ui.common.sectionContainer
import com.rideminiapp.ui.common.screenRoot
import com.rideminiapp.ui.common.titleView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DriverToolsFragment : Fragment() {
    @Inject lateinit var rideRepository: RideRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll: ScrollView = context.screenRoot()
        val root = context.sectionContainer()
        val output = TextView(context)

        root.addView(context.titleView("Driver tools"))
        root.addView(context.bodyView("Offers, notifications, and registration support screens."))
        root.addView(output)

        root.addView(context.primaryButton("Load offers") {
            lifecycleScope.launch {
                runCatching { rideRepository.driverOffers() }
                    .onSuccess { output.text = "Driver offers: ${it.items.size}" }
                    .onFailure { output.text = it.message ?: "Failed to load offers." }
            }
        })

        root.addView(context.primaryButton("Load notifications") {
            lifecycleScope.launch {
                runCatching { rideRepository.driverNotifications() }
                    .onSuccess { output.text = "Driver notifications: ${it.items.size}" }
                    .onFailure { output.text = it.message ?: "Failed to load notifications." }
            }
        })

        root.addView(context.primaryButton("Unread count") {
            lifecycleScope.launch {
                runCatching { rideRepository.driverUnreadNotificationCount() }
                    .onSuccess { output.text = it.toString() }
                    .onFailure { output.text = it.message ?: "Failed to load unread count." }
            }
        })

        root.addView(context.primaryButton("Driver registration form") {
            lifecycleScope.launch {
                runCatching { rideRepository.driverRegistrationForm() }
                    .onSuccess { output.text = it.toString() }
                    .onFailure { output.text = it.message ?: "Failed to load registration form." }
            }
        })

        root.addView(context.primaryButton("My application") {
            lifecycleScope.launch {
                runCatching { rideRepository.myDriverApplication() }
                    .onSuccess { output.text = it.toString() }
                    .onFailure { output.text = it.message ?: "Failed to load application." }
            }
        })

        root.addView(context.primaryButton("Enter URL") {
            lifecycleScope.launch {
                runCatching { rideRepository.driverCabinetEnterUrl() }
                    .onSuccess { output.text = it.toString() }
                    .onFailure { output.text = it.message ?: "Failed to load enter URL." }
            }
        })

        root.addView(context.primaryButton("Bootstrap access") {
            lifecycleScope.launch {
                runCatching { rideRepository.bootstrapDriverAccess() }
                    .onSuccess { output.text = it.toString() }
                    .onFailure { output.text = it.message ?: "Failed to bootstrap access." }
            }
        })

        scroll.addView(root)
        return scroll
    }
}
