package com.rideminiapp.ui.auth

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.BuildConfig
import com.rideminiapp.data.repository.SessionRepository
import com.rideminiapp.ui.common.*
import com.rideminiapp.ui.common.openTelegramHandoff
import com.rideminiapp.ui.root.BootstrapFragment
import com.rideminiapp.ui.auth.RoleSelectionFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TelegramHandoffFragment : Fragment() {
    @Inject lateinit var sessionRepository: SessionRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll = context.screenRoot()
        val root = context.sectionContainer()
        root.addView(context.titleView("Telegram auth"))
        root.addView(context.bodyView("Opening Telegram for authentication..."))

        scroll.addView(root)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        replaceAfterAuth()
    }

    override fun onResume() {
        super.onResume()
        val state = sessionRepository.loadState()
        if (state.passengerToken.isNullOrBlank()) {
            openTelegramOnce(null)
        } else {
            replaceAfterAuth()
        }
    }

    private fun openTelegramOnce(status: TextView? = null) {
        val handoffUrl = sessionRepository.loadState().handoffUrl.ifBlank { BuildConfig.DEFAULT_HANDOFF_URL }
        if (sessionRepository.hasTelegramHandoffAttempted()) {
            status?.text = "Waiting for Telegram auth..."
            return
        }
        sessionRepository.markTelegramHandoffAttempted()
        if (!openTelegramHandoff(handoffUrl)) {
            status?.text = "Failed to open Telegram."
        }
    }

    private fun replaceAfterAuth() {
        val state = sessionRepository.loadState()
        if (!state.passengerToken.isNullOrBlank() && state.role == null) {
            replaceRoot(RoleSelectionFragment())
        } else if (!state.passengerToken.isNullOrBlank()) {
            replaceRoot(BootstrapFragment())
        }
    }
}
