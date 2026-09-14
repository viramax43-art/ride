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
class PassengerRequestsFragment : Fragment() {
    @Inject lateinit var rideRepository: RideRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll = context.screenRoot()
        val root = context.sectionContainer()
        root.addView(context.titleView("My requests"))
        val list = TextView(context)
        root.addView(list)

        lifecycleScope.launch {
            runCatching { rideRepository.myRequests() }
                .onSuccess { page ->
                    list.text = page.items.joinToString("\n\n") { item ->
                        "#${item.rideNumber} ${item.status}\n${item.fromPoint.address} -> ${item.toPoint.address}"
                    }.ifBlank { "No requests yet." }
                }
                .onFailure { list.text = it.message ?: "Failed to load requests." }
        }

        scroll.addView(root)
        return scroll
    }
}
