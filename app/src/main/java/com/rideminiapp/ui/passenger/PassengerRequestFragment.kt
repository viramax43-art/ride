package com.rideminiapp.ui.passenger

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.data.repository.RideRepository
import com.rideminiapp.ui.common.*
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PassengerRequestFragment : Fragment() {
    @Inject lateinit var rideRepository: RideRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll = context.screenRoot()
        val root = context.sectionContainer()
        root.addView(context.titleView("New ride"))
        root.addView(context.bodyView("This is the native baseline for the passenger flow. The full map picker will be filled in on top of this contract layer."))

        val from = context.inputLayout("From address")
        val to = context.inputLayout("To address")
        val dateTime = context.inputLayout("DateTime ISO")
        root.addView(from)
        root.addView(to)
        root.addView(dateTime)

        root.addView(context.primaryButton("Get quote") {
            lifecycleScope.launch {
                runCatching {
                    rideRepository.rideQuote(54.6872, 25.2797, 54.7, 25.3)
                }
            }
        })

        root.addView(context.primaryButton("Create request") {
            val fromText = from.editTextValue()
            val toText = to.editTextValue()
            val dt = dateTime.editTextValue()
            if (fromText.isNotBlank() && toText.isNotBlank() && dt.isNotBlank()) {
                lifecycleScope.launch {
                    rideRepository.createRequest(
                        passengerName = "Passenger",
                        fromAddress = fromText,
                        fromLat = 54.6872,
                        fromLng = 25.2797,
                        toAddress = toText,
                        toLat = 54.7,
                        toLng = 25.3,
                        dateTime = dt,
                    )
                }
            }
        })
        scroll.addView(root)
        return scroll
    }
}
