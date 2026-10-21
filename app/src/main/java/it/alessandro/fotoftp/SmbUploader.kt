package it.alessandro.fotoftp

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Upload su cartella condivisa Windows/NAS (SMB2/SMB3). */
object SmbUploader {

    /** Carica [file] nella cartella configurata. Lancia IOException in caso di errore. */
    fun upload(settings: FtpSettings, file: File) {
        withShare(settings) { share, dir ->
            val target = if (dir.isEmpty()) file.name else "$dir\\${file.name}"
            share.openFile(
                target,
                setOf(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null,
            ).use { remote ->
                remote.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
            }
        }
    }

    /** Verifica connessione, login, condivisione e cartella. */
    fun testConnection(settings: FtpSettings) {
        withShare(settings) { _, _ -> }
    }

    private fun withShare(settings: FtpSettings, block: (DiskShare, String) -> Unit) {
        // "Foto\Cantiere" nel campo condivisione: la prima parte è la condivisione, il resto la cartella.
        val shareParts = splitPath(settings.share)
        val shareName = shareParts.firstOrNull() ?: throw IOException("Condivisione mancante")
        val dirParts = shareParts.drop(1) + splitPath(settings.remoteDir)

        // Accetta anche "DOMINIO\utente" o "utente@dominio" nel campo utente.
        var user = settings.username.trim()
        var domain = settings.domain.trim()
        if (domain.isEmpty() && '\\' in user) {
            domain = user.substringBefore('\\'); user = user.substringAfter('\\')
        } else if (domain.isEmpty() && '@' in user) {
            domain = user.substringAfter('@'); user = user.substringBefore('@')
        }

        val config = SmbConfig.builder()
            .withSecurityProvider(BCSecurityProvider())
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build()
        try {
            SMBClient(config).use { client ->
                client.connect(settings.host.trim(), settings.port).use { connection ->
                    val auth = AuthenticationContext(user, settings.password.toCharArray(), domain.ifEmpty { null })
                    connection.authenticate(auth).use { session ->
                        val share = session.connectShare(shareName) as? DiskShare
                            ?: throw IOException("\"$shareName\" non è una cartella condivisa")
                        share.use { block(it, ensureDir(it, dirParts)) }
                    }
                }
            }
        } catch (e: SMBApiException) {
            throw IOException(describe(e.status, shareName), e)
        }
    }

    /** Crea le sottocartelle mancanti e restituisce il percorso finale (separato da "\"). */
    private fun ensureDir(share: DiskShare, parts: List<String>): String {
        var path = ""
        for (part in parts) {
            path = if (path.isEmpty()) part else "$path\\$part"
            if (!share.folderExists(path)) share.mkdir(path)
        }
        return path
    }

    private fun splitPath(p: String) = p.split('/', '\\').map { it.trim() }.filter { it.isNotEmpty() }

    private fun describe(status: NtStatus, shareName: String) = when (status) {
        NtStatus.STATUS_LOGON_FAILURE -> "Login fallito: controlla utente, password e dominio"
        NtStatus.STATUS_ACCOUNT_DISABLED -> "Account disabilitato"
        NtStatus.STATUS_PASSWORD_EXPIRED -> "Password scaduta"
        NtStatus.STATUS_BAD_NETWORK_NAME -> "Condivisione \"$shareName\" non trovata sul server"
        NtStatus.STATUS_ACCESS_DENIED -> "Accesso negato: l'utente non ha i permessi di scrittura"
        else -> "Errore SMB: $status"
    }
}
