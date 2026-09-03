package id.orbitcontrol.data

import android.content.Context

data class SavedLoginConfig(val host: String = "http://192.168.8.1", val username: String = "admin", val saveConfig: Boolean = false)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("orbit_control", Context.MODE_PRIVATE)

    fun load(): SavedLoginConfig = SavedLoginConfig(
        host = prefs.getString("host", null) ?: "http://192.168.8.1",
        username = prefs.getString("username", null) ?: "admin",
        saveConfig = prefs.getBoolean("save_config", false),
    )

    fun save(host: String, username: String, enabled: Boolean) {
        prefs.edit().apply {
            remove("host").remove("username").remove("save_config")
            if (enabled) putString("host", host).putString("username", username).putBoolean("save_config", true)
        }.apply()
    }

}
