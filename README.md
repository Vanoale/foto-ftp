# Foto FTP

App Android minimale: scatti una foto, viene salvata in JPEG sul telefono e caricata
in automatico in una cartella del tuo server FTP.

## Come funziona
1. Al primo avvio si apre **Impostazioni FTP**: server, porta, utente, password,
   cartella remota, modalità passiva e FTPS (opzionale). Con **Prova connessione** verifichi
   che tutto sia corretto, poi **Salva**.
2. Nella schermata principale premi **Scatta foto**: si apre la fotocamera del telefono.
3. La foto viene salvata come `IMG_AAAAMMGG_HHMMSS.jpg` in
   `Android/data/it.alessandro.fotoftp/files/Pictures/` e messa in coda per l'upload.
4. L'upload parte subito se c'è rete, altrimenti appena torna la connessione, con fino a
   5 tentativi automatici. Lo stato di ogni foto è visibile nella lista "Upload recenti".
   Se la cartella remota non esiste viene creata.

## Compilare l'APK
- **Android Studio**: apri questa cartella e premi Run, oppure *Build → Build APK(s)*.
- **Da riga di comando** (con Android SDK installato): `./gradlew assembleDebug`
  → `app/build/outputs/apk/debug/app-debug.apk`
- **GitHub**: caricando il progetto in un repository, il workflow
  `.github/workflows/build.yml` compila l'APK a ogni push e lo allega come artifact.

## Note
- Stack: Kotlin, Jetpack Compose, WorkManager, Apache Commons Net. Android 7.0+ (minSdk 24).
- La password FTP è salvata nelle preferenze private dell'app sul telefono.
- FTP semplice trasmette la password in chiaro: se il server lo supporta attiva FTPS.
