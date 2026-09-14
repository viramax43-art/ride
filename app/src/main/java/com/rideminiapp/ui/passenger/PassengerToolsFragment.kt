package com.rideminiapp.ui.passenger

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.data.repository.RideRepository
import com.rideminiapp.ui.common.bodyView
import com.rideminiapp.ui.common.editTextValue
import com.rideminiapp.ui.common.inputLayout
import com.rideminiapp.ui.common.primaryButton
import com.rideminiapp.ui.common.sectionContainer
import com.rideminiapp.ui.common.screenRoot
import com.rideminiapp.ui.common.titleView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PassengerToolsFragment : Fragment() {
    @Inject lateinit var rideRepository: RideRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll: ScrollView = context.screenRoot()
        val root = context.sectionContainer()
        val output = TextView(context)
        val qrPoints = context.inputLayout("QR points")
        val transferRecipient = context.inputLayout("Transfer recipient userId")
        val transferPoints = context.inputLayout("Transfer points")
        val cardPoints = context.inputLayout("Card purchase points")

        root.addView(context.titleView("Passenger tools"))
        root.addView(context.bodyView("Quick access to points, offers, and notifications."))
        root.addView(output)
        root.addView(qrPoints)
        root.addView(transferRecipient)
        root.addView(transferPoints)
        root.addView(cardPoints)

        root.addView(context.primaryButton("Load pricing") {
            lifecycleScope.launch {
                runCatching { rideRepository.pricing() }
                    .onSuccess { output.text = "pricingMode=${it.pricingMode}\npointsPerRide=${it.pointsPerRide}\npointPriceCents=${it.pointPriceCents}" }
                    .onFailure { output.text = it.message ?: "Failed to load pricing." }
            }
        })

        root.addView(context.primaryButton("Load offers") {
            lifecycleScope.launch {
                runCatching { rideRepository.passengerRideOffers() }
                    .onSuccess { output.text = "Passenger offers: ${it.items.size}" }
                    .onFailure { output.text = it.message ?: "Failed to load offers." }
            }
        })

        root.addView(context.primaryButton("Load notifications") {
            lifecycleScope.launch {
                runCatching { rideRepository.passengerNotifications() }
                    .onSuccess { output.text = "Passenger notifications: ${it.items.size}" }
                    .onFailure { output.text = it.message ?: "Failed to load notifications." }
            }
        })

        root.addView(context.primaryButton("Issue QR") {
            val points = qrPoints.editTextValue().toIntOrNull() ?: return@primaryButton
            lifecycleScope.launch {
                runCatching { rideRepository.issuePointsQr(mapOf("points" to points)) }
                    .onSuccess { output.text = it.toString() }
                    .onFailure { output.text = it.message ?: "Failed to issue QR." }
            }
        })

        root.addView(context.primaryButton("Buy points") {
            val points = cardPoints.editTextValue().toIntOrNull() ?: return@primaryButton
            lifecycleScope.launch {
                runCatching { rideRepository.purchasePointsCard(mapOf("points" to points)) }
                    .onSuccess { output.text = it.toString() }
                    .onFailure { output.text = it.message ?: "Failed to buy points." }
            }
        })

        root.addView(context.primaryButton("Transfer points") {
            val recipient = transferRecipient.editTextValue()
            val points = transferPoints.editTextValue().toIntOrNull() ?: return@primaryButton
            if (recipient.isBlank()) return@primaryButton
            lifecycleScope.launch {
                runCatching { rideRepository.transferPoints(mapOf("recipientUserId" to recipient, "points" to points)) }
                    .onSuccess { output.text = it.toString() }
                    .onFailure { output.text = it.message ?: "Failed to transfer points." }
            }
        })

        scroll.addView(root)
        return scroll
    }
}
