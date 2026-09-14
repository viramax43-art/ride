package com.rideminiapp.ui.auth

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.domain.AdminPolicy
import com.rideminiapp.data.repository.SessionRepository
import com.rideminiapp.domain.Role
import com.rideminiapp.ui.common.bodyView
import com.rideminiapp.ui.common.openTelegramHandoff
import com.rideminiapp.ui.common.primaryButton
import com.rideminiapp.ui.common.replaceRoot
import com.rideminiapp.ui.common.sectionContainer
import com.rideminiapp.ui.common.screenRoot
import com.rideminiapp.ui.common.titleView
import com.rideminiapp.BuildConfig
import com.rideminiapp.ui.root.BootstrapFragment
import com.rideminiapp.ui.web.WebAppFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RoleSelectionFragment : Fragment() {
    private companion object {
        const val TAG = "RoleSelectionFragment"
    }

    @Inject lateinit var sessionRepository: SessionRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll: ScrollView = context.screenRoot()
        val root = context.sectionContainer()
        val state = sessionRepository.loadState()

        root.addView(context.titleView("Choose role"))
        root.addView(context.bodyView("Pick how you want to continue. Passenger uses Telegram auth. Driver and admin continue with their keys."))

        if (state.passengerToken.isNullOrBlank()) {
            root.addView(context.bodyView("Telegram auth has not been completed yet."))
        } else {
            root.addView(context.bodyView("Telegram auth is ready."))
        }
        val isChiefAdmin = AdminPolicy.isChiefAdminUsername(state.currentUsername)
        if (!isChiefAdmin) {
            root.addView(context.bodyView("Admin access is available only for approved Telegram accounts."))
        }

        root.addView(context.primaryButton("Passenger") {
            val tokenAvailable = !sessionRepository.loadState().passengerToken.isNullOrBlank()
            if (!tokenAvailable) {
                openTelegramAndFinish()
                return@primaryButton
            }
            lifecycleScope.launch {
                sessionRepository.saveRole(Role.PASSENGER)
                replaceRoot(BootstrapFragment())
            }
        })

        root.addView(context.primaryButton("Driver") {
            replaceRoot(DriverLoginFragment())
        })

        if (isChiefAdmin) {
            root.addView(context.primaryButton("Admin") {
                replaceRoot(WebAppFragment.newInstance())
            })
        }

        scroll.addView(root)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val state = sessionRepository.loadState()
        Log.d(TAG, "viewCreated username=${state.currentUsername} role=${state.role} adminCookie=${state.adminCookieReady}")
    }

    private fun openTelegramAndFinish() {
        val handoffUrl = sessionRepository.loadState().handoffUrl.ifBlank { BuildConfig.DEFAULT_HANDOFF_URL }
        if (openTelegramHandoff(handoffUrl)) {
            activity?.finish()
        }
    }
}
