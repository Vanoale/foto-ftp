package it.alessandro.fotoftp

import android.content.Context

data class FtpSettings(
    val host: String = "",
    val port: Int = 21,
    val username: String = "",
    val password: String = "",
    val remoteDir: String = "/",
    val passiveMode: Boolean = true,
    val useFtps: Boolean = false,
) {
    val isComplete: Boolean get() = host.isNotBlank() && username.isNotBlank()
}

/** Salva le impostazioni FTP sul telefono (SharedPreferences privata dell'app). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ftp_settings", Context.MODE_PRIVATE)

    fun load() = FtpSettings(
        host = prefs.getString("host", "")!!,
        port = prefs.getInt("port", 21),
        username = prefs.getString("username", "")!!,
        password = prefs.getString("password", "")!!,
        remoteDir = prefs.getString("remoteDir", "/")!!,
        passiveMode = prefs.getBoolean("passiveMode", true),
        useFtps = prefs.getBoolean("useFtps", false),
    )

    fun save(s: FtpSettings) {
        prefs.edit()
            .putString("host", s.host.trim())
            .putInt("port", s.port)
            .putString("username", s.username.trim())
            .putString("password", s.password)
            .putString("remoteDir", s.remoteDir.trim().ifEmpty { "/" })
            .putBoolean("passiveMode", s.passiveMode)
            .putBoolean("useFtps", s.useFtps)
            .apply()
    }
}
