package com.clawd.pet

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("clawd_pet", Context.MODE_PRIVATE)

    var enabled: Boolean by BoolPref("enabled", true)
    var size: Int by IntPref("size", 1)
    var color: Int by IntPref("color", 0)
    var accessory: Int by IntPref("accessory", 0)
    var speed: Int by IntPref("speed", 1)
    var gravity: Boolean by BoolPref("gravity", false)
    var wander: Boolean by BoolPref("wander", true)
    var phrases: Boolean by BoolPref("phrases", true)
    var phraseFreq: Int by IntPref("phrase_freq", 1)
    var sleep: Int by IntPref("sleep", 1)
    var haptics: Boolean by BoolPref("haptics", true)
    var hungerEnabled: Boolean by BoolPref("hunger_enabled", true)
    var hungerRate: Int by IntPref("hunger_rate", 1)
    var autostart: Boolean by BoolPref("autostart", false)
    var sensors: Boolean by BoolPref("sensors", true)
    var snacks: Boolean by BoolPref("snacks", true)
    var snackSpeed: Int by IntPref("snack_speed", 1)
    var cling: Boolean by BoolPref("cling", true)
    var petName: String by StringPref("pet_name", "Clawd")

    var petsCount: Int by IntPref("pets_count", 0)
    var feedCount: Int by IntPref("feed_count", 0)
    var playCount: Int by IntPref("play_count", 0)
    var bornAt: Long by LongPref("born_at", 0L)

    var posX: Int by IntPref("pos_x", 800)
    var posY: Int by IntPref("pos_y", 720)
    var lastFedAt: Long by LongPref("last_fed_at", 0L)

    fun ensureBorn() {
        if (bornAt == 0L) bornAt = System.currentTimeMillis()
        if (lastFedAt == 0L) lastFedAt = System.currentTimeMillis()
    }

    fun ageDays(): Int {
        if (bornAt == 0L) return 0
        val ms = System.currentTimeMillis() - bornAt
        return (ms / (24L * 3600_000L)).toInt()
    }

    fun resetStats() {
        petsCount = 0
        feedCount = 0
        playCount = 0
        bornAt = System.currentTimeMillis()
        lastFedAt = System.currentTimeMillis()
    }

    private inner class BoolPref(val key: String, val def: Boolean) {
        operator fun getValue(t: Any?, p: Any?): Boolean = sp.getBoolean(key, def)
        operator fun setValue(t: Any?, p: Any?, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    }

    private inner class IntPref(val key: String, val def: Int) {
        operator fun getValue(t: Any?, p: Any?): Int = sp.getInt(key, def)
        operator fun setValue(t: Any?, p: Any?, v: Int) = sp.edit().putInt(key, v).apply()
    }

    private inner class LongPref(val key: String, val def: Long) {
        operator fun getValue(t: Any?, p: Any?): Long = sp.getLong(key, def)
        operator fun setValue(t: Any?, p: Any?, v: Long) = sp.edit().putLong(key, v).apply()
    }

    private inner class StringPref(val key: String, val def: String) {
        operator fun getValue(t: Any?, p: Any?): String = sp.getString(key, def) ?: def
        operator fun setValue(t: Any?, p: Any?, v: String) = sp.edit().putString(key, v).apply()
    }
}
