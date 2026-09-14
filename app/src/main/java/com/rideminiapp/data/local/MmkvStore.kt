package com.rideminiapp.data.local

import com.tencent.mmkv.MMKV

class MmkvStore {
    private val kv: MMKV = MMKV.defaultMMKV()

    fun getString(key: String, defaultValue: String = ""): String = kv.decodeString(key, defaultValue) ?: defaultValue

    fun putString(key: String, value: String) {
        kv.encode(key, value)
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean = kv.decodeBool(key, defaultValue)

    fun putBoolean(key: String, value: Boolean) {
        kv.encode(key, value)
    }

    fun remove(key: String) {
        kv.removeValueForKey(key)
    }
}
