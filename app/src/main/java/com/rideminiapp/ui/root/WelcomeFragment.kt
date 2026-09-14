package com.rideminiapp.ui.root

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.data.repository.SessionRepository
import com.rideminiapp.ui.auth.TelegramHandoffFragment
import com.rideminiapp.ui.common.bodyView
import com.rideminiapp.ui.common.primaryButton
import com.rideminiapp.ui.common.replaceRoot
import com.rideminiapp.ui.common.sectionContainer
import com.rideminiapp.ui.common.screenRoot
import com.rideminiapp.ui.common.titleView
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WelcomeFragment : Fragment() {
    @Inject lateinit var sessionRepository: SessionRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll: ScrollView = context.screenRoot()
        val root = context.sectionContainer()

        root.addView(context.titleView("RIDE"))
        root.addView(context.bodyView("Welcome. We will open Telegram for authentication, then bring you back here to choose your role."))
        root.addView(context.primaryButton("Open Telegram") {
            sessionRepository.markWelcomeRedirected()
            replaceRoot(TelegramHandoffFragment())
        })

        scroll.addView(root)
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState == null && sessionRepository.shouldAutoRedirectWelcome()) {
            sessionRepository.markWelcomeRedirected()
            viewLifecycleOwner.lifecycleScope.launch {
                delay(550)
                if (isAdded) {
                    replaceRoot(TelegramHandoffFragment())
                }
            }
        }
    }
}
