package it.alessandro.fotoftp

import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.IOException

object FtpUploader {

    /** Carica [file] nella cartella remota configurata. Lancia IOException in caso di errore. */
    fun upload(settings: FtpSettings, file: File) {
        withClient(settings) { client ->
            changeToDir(client, settings.remoteDir)
            val ok = file.inputStream().use { client.storeFile(file.name, it) }
            if (!ok) throw IOException("Upload fallito: ${client.replyString.trim()}")
        }
    }

    /** Verifica connessione, login e accesso alla cartella remota. */
    fun testConnection(settings: FtpSettings) {
        withClient(settings) { client -> changeToDir(client, settings.remoteDir) }
    }

    private fun withClient(settings: FtpSettings, block: (FTPClient) -> Unit) {
        val client = if (settings.useFtps) FTPSClient(false) else FTPClient()
        client.connectTimeout = 15_000
        client.setDefaultTimeout(30_000)
        try {
            client.connect(settings.host, settings.port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                throw IOException("Il server ha rifiutato la connessione: ${client.replyString.trim()}")
            }
            if (!client.login(settings.username, settings.password)) {
                throw IOException("Login fallito: controlla utente e password")
            }
            if (client is FTPSClient) {
                client.execPBSZ(0)
                client.execPROT("P")
            }
            client.setFileType(FTP.BINARY_FILE_TYPE)
            if (settings.passiveMode) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
            block(client)
            client.logout()
        } finally {
            if (client.isConnected) {
                try { client.disconnect() } catch (_: IOException) { }
            }
        }
    }

    /** Entra nella cartella remota, creando le sottocartelle mancanti. */
    private fun changeToDir(client: FTPClient, dir: String) {
        val path = dir.trim()
        if (path.isEmpty() || path == "/") {
            client.changeWorkingDirectory("/")
            return
        }
        if (path.startsWith("/")) client.changeWorkingDirectory("/")
        for (part in path.split("/").filter { it.isNotBlank() }) {
            if (!client.changeWorkingDirectory(part)) {
                if (!client.makeDirectory(part) || !client.changeWorkingDirectory(part)) {
                    throw IOException("Impossibile aprire o creare la cartella \"$part\": ${client.replyString.trim()}")
                }
            }
        }
    }
}
