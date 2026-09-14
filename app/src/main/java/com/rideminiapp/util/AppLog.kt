package com.rideminiapp.util

import android.util.Log
import com.rideminiapp.BuildConfig

object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable == null) {
                Log.w(tag, message)
            } else {
                Log.w(tag, message, throwable)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable == null) {
                Log.e(tag, message)
            } else {
                Log.e(tag, message, throwable)
            }
        }
    }
}
