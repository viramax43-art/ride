package com.rideminiapp.ui.auth

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.rideminiapp.data.repository.SessionRepository
import com.rideminiapp.ui.common.*
import com.rideminiapp.ui.driver.DriverShellFragment
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DriverLoginFragment : Fragment() {
    @Inject lateinit var sessionRepository: SessionRepository

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val scroll: ScrollView = context.screenRoot()
        val root = context.sectionContainer()
        root.addView(context.titleView("Driver login"))
        val key = context.inputLayout("Driver key")
        root.addView(key)
        root.addView(context.primaryButton("Login") {
            val entered = key.editTextValue()
            if (entered.isNotBlank()) {
                lifecycleScope.launch {
                    sessionRepository.loginDriverWithKey(entered)
                    replaceRoot(DriverShellFragment())
                }
            }
        })
        root.addView(context.primaryButton("Back") { replaceRoot(RoleSelectionFragment()) })
        scroll.addView(root)
        return scroll
    }
}
