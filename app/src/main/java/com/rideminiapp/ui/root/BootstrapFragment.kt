package com.rideminiapp.ui.root

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.BuildConfig
import com.rideminiapp.domain.AdminPolicy
import com.rideminiapp.domain.Role
import com.rideminiapp.data.repository.RideRepository
import com.rideminiapp.data.repository.SessionRepository
import com.rideminiapp.ui.auth.DriverLoginFragment
import com.rideminiapp.ui.auth.RoleSelectionFragment
import com.rideminiapp.ui.common.replaceRoot
import com.rideminiapp.ui.common.openTelegramHandoff
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import com.rideminiapp.ui.driver.DriverShellFragment
import com.rideminiapp.ui.passenger.PassengerOnboardingFragment
import com.rideminiapp.ui.passenger.PassengerShellFragment
import com.rideminiapp.ui.web.WebAppFragment

@AndroidEntryPoint
class BootstrapFragment : Fragment() {
    private companion object {
        const val TAG = "BootstrapFragment"
    }

    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var rideRepository: RideRepository

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { sessionRepository.loadState() }
            Log.d(TAG, "bootstrap state username=${state.currentUsername} role=${state.role} passengerToken=${state.passengerToken != null} driverCookie=${state.driverCookieReady} adminCookie=${state.adminCookieReady}")
            val currentUser = withContext(Dispatchers.IO) {
                if (state.passengerToken.isNullOrBlank()) null else runCatching { rideRepository.currentUser() }.getOrNull()
            }
            Log.d(TAG, "bootstrap currentUser username=${currentUser?.username} role=${currentUser?.role} onboardingCompleted=${currentUser?.onboardingCompleted}")

            if (currentUser == null) {
                if (state.passengerToken.isNullOrBlank()) {
                    Log.d(TAG, "bootstrap route -> telegram because passenger token is missing")
                    openTelegramOnceAndFinish()
                    return@launch
                }
                Log.d(TAG, "bootstrap currentUser is null but passenger token exists, keeping local auth")
            }

            if (AdminPolicy.isChiefAdminUsername(currentUser?.username) || AdminPolicy.isChiefAdminUsername(state.currentUsername)) {
                Log.d(TAG, "bootstrap route -> admin for chief admin username")
                val adminUsername = currentUser?.username?.takeIf(AdminPolicy::isChiefAdminUsername)
                    ?: state.currentUsername?.takeIf(AdminPolicy::isChiefAdminUsername)
                sessionRepository.saveCurrentUsername(adminUsername)
                sessionRepository.saveRole(Role.ADMIN)
                replaceRoot(WebAppFragment.newInstance())
                return@launch
            }

            val role = withContext(Dispatchers.IO) { sessionRepository.resolveRoleOrNull() }
            Log.d(TAG, "bootstrap resolvedRole=$role")
            when (role) {
                Role.PASSENGER -> {
                    Log.d(TAG, "bootstrap route -> passenger")
                    when {
                        currentUser == null -> replaceRoot(PassengerShellFragment())
                        currentUser.onboardingCompleted -> replaceRoot(PassengerShellFragment())
                        else -> replaceRoot(PassengerOnboardingFragment())
                    }
                }
                Role.DRIVER -> {
                    Log.d(TAG, "bootstrap route -> driver")
                    if (state.driverCookieReady) replaceRoot(DriverShellFragment()) else replaceRoot(DriverLoginFragment())
                }
                Role.ADMIN, Role.MODERATOR -> {
                    Log.d(TAG, "bootstrap route -> admin/moderator")
                    replaceRoot(WebAppFragment.newInstance())
                }
                null -> {
                    Log.d(TAG, "bootstrap route -> null")
                    when {
                        !state.passengerToken.isNullOrBlank() -> replaceRoot(RoleSelectionFragment())
                        else -> openTelegramOnceAndFinish()
                    }
                }
            }
        }
    }

    private fun openTelegramOnceAndFinish() {
        val handoffUrl = sessionRepository.loadState().handoffUrl.ifBlank { BuildConfig.DEFAULT_HANDOFF_URL }
        if (sessionRepository.hasTelegramHandoffAttempted()) {
            Log.d(TAG, "telegram handoff already attempted, skipping open")
            return
        }
        sessionRepository.markTelegramHandoffAttempted()
        if (openTelegramHandoff(handoffUrl)) {
            activity?.finish()
        }
    }
}
