package com.rideminiapp.ui.common

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.updatePadding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

fun Context.screenRoot(): ScrollView = ScrollView(this).apply {
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
}

fun Context.sectionContainer(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    updatePadding(left = dp(16), right = dp(16), top = dp(16), bottom = dp(24))
}

fun Context.cardContainer(): MaterialCardView = MaterialCardView(this).apply {
    radius = dp(20).toFloat()
    cardElevation = dp(2).toFloat()
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}

fun Context.titleView(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 22f
    setTypeface(typeface, Typeface.BOLD)
}

fun Context.bodyView(text: String): TextView = TextView(this).apply {
    this.text = text
    textSize = 14f
}

fun Context.primaryButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
    this.text = text
    setOnClickListener { onClick() }
}

fun Context.inputLayout(hint: String, multiline: Boolean = false): TextInputLayout {
    val layout = TextInputLayout(this).apply { this.hint = hint }
    val field = TextInputEditText(this).apply {
        if (multiline) minLines = 3
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    layout.addView(field)
    return layout
}

fun TextInputLayout.editTextValue(): String = (editText as? EditText)?.text?.toString().orEmpty()

fun Context.dp(value: Int): Int = (resources.displayMetrics.density * value).toInt()
