package com.rideminiapp.ui.common

import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.rideminiapp.R

fun Fragment.replaceRoot(fragment: Fragment) {
    parentFragmentManager.commit {
        setReorderingAllowed(true)
        replace(android.R.id.content, fragment)
    }
}

fun Fragment.replaceChild(containerId: Int, fragment: Fragment) {
    childFragmentManager.commit {
        setReorderingAllowed(true)
        replace(containerId, fragment)
    }
}
