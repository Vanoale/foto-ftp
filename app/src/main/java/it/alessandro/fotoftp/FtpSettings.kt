package it.alessandro.fotoftp

import android.content.Context

enum class Protocol(val defaultPort: Int) { FTP(21), SMB(445) }

data class FtpSettings(
    val protocol: Protocol = Protocol.FTP,
    val host: String = "",
    val port: Int = 21,
    val username: String = "",
    val password: String = "",
    val remoteDir: String = "/",
    val passiveMode: Boolean = true,
    val useFtps: Boolean = false,
    /** Solo SMB: nome della cartella condivisa (es. "Foto"). */
    val share: String = "",
    /** Solo SMB: dominio Windows, vuoto per utenti locali. */
    val domain: String = "",
) {
    val isComplete: Boolean
        get() = host.isNotBlank() && username.isNotBlank() &&
            (protocol != Protocol.SMB || share.isNotBlank())
}

/** Salva le impostazioni di upload sul telefono (SharedPreferences privata dell'app). */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ftp_settings", Context.MODE_PRIVATE)

    fun load() = FtpSettings(
        protocol = runCatching { Protocol.valueOf(prefs.getString("protocol", "FTP")!!) }.getOrDefault(Protocol.FTP),
        host = prefs.getString("host", "")!!,
        port = prefs.getInt("port", 21),
        username = prefs.getString("username", "")!!,
        password = prefs.getString("password", "")!!,
        remoteDir = prefs.getString("remoteDir", "/")!!,
        passiveMode = prefs.getBoolean("passiveMode", true),
        useFtps = prefs.getBoolean("useFtps", false),
        share = prefs.getString("share", "")!!,
        domain = prefs.getString("domain", "")!!,
    )

    fun save(s: FtpSettings) {
        prefs.edit()
            .putString("protocol", s.protocol.name)
            .putString("host", s.host.trim())
            .putInt("port", s.port)
            .putString("username", s.username.trim())
            .putString("password", s.password)
            .putString("remoteDir", s.remoteDir.trim().ifEmpty { "/" })
            .putBoolean("passiveMode", s.passiveMode)
            .putBoolean("useFtps", s.useFtps)
            .putString("share", s.share.trim())
            .putString("domain", s.domain.trim())
            .apply()
    }
}

/** Sceglie FTP o SMB in base alle impostazioni. */
object Uploader {
    fun upload(settings: FtpSettings, file: java.io.File) = when (settings.protocol) {
        Protocol.FTP -> FtpUploader.upload(settings, file)
        Protocol.SMB -> SmbUploader.upload(settings, file)
    }

    fun testConnection(settings: FtpSettings) = when (settings.protocol) {
        Protocol.FTP -> FtpUploader.testConnection(settings)
        Protocol.SMB -> SmbUploader.testConnection(settings)
    }
}
