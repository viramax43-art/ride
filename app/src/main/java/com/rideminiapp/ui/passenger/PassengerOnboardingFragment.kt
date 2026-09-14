package com.rideminiapp.ui.passenger

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.data.repository.RideRepository
import com.rideminiapp.data.repository.SessionRepository
import com.rideminiapp.ui.common.bodyView
import com.rideminiapp.ui.common.primaryButton
import com.rideminiapp.ui.common.replaceRoot
import com.rideminiapp.ui.common.sectionContainer
import com.rideminiapp.ui.common.screenRoot
import com.rideminiapp.ui.common.titleView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PassengerOnboardingFragment : Fragment() {
    @Inject lateinit var sessionRepository: SessionRepository
    @Inject lateinit var rideRepository: RideRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll: ScrollView = context.screenRoot()
        val root = context.sectionContainer()
        val status = TextView(context)

        root.addView(context.titleView("Welcome"))
        root.addView(context.bodyView("Pick your language and finish onboarding before opening the passenger shell."))
        root.addView(status)

        fun setLanguage(language: String) {
            lifecycleScope.launch {
                runCatching { rideRepository.updateLanguage(language) }
                    .onSuccess {
                        sessionRepository.saveLanguage(language)
                        status.text = "Language saved: $language"
                    }
                    .onFailure {
                        status.text = it.message ?: "Failed to save language."
                    }
            }
        }

        root.addView(context.primaryButton("Lithuanian") { setLanguage("lt") })
        root.addView(context.primaryButton("Polish") { setLanguage("pl") })
        root.addView(context.primaryButton("English") { setLanguage("en") })
        root.addView(context.primaryButton("Russian") { setLanguage("ru") })

        root.addView(context.primaryButton("Complete onboarding") {
            lifecycleScope.launch {
                runCatching { rideRepository.completeOnboarding() }
                    .onSuccess {
                        status.text = "Onboarding completed."
                        replaceRoot(PassengerShellFragment())
                    }
                    .onFailure {
                        status.text = it.message ?: "Failed to complete onboarding."
                    }
            }
        })

        scroll.addView(root)
        return scroll
    }
}
