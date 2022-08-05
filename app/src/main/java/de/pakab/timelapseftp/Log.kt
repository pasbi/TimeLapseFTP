package de.pakab.timelapseftp

import android.content.Context
import android.os.BatteryManager

open class Log() {
    private var log = ""
    private var context: Context? = null

    fun logStatus() {
        if (context == null) {
            log("Context is null, hence some values cannot be retrieved.")
            return
        }
        val bm = context!!.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        log("Battery: $batLevel%")
    }

    fun log(message: String) {
        log = "$message\n$log"
        onLog()
    }

    fun log(): String {
        return log
    }

    protected open fun onLog() { }

    fun setContext(context: Context) {
        this.context = context
    }
}
