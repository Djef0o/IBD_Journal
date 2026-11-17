# IBD Journal

[English](../../README.md) · [Français](README.fr.md) · **Deutsch** · [Español](README.es.md) · [Italiano](README.it.md) · [Português](README.pt.md)

Ein Android-Tagebuch für Menschen mit Morbus Crohn oder Colitis ulcerosa. Halte Stuhlgang und Beschwerden mit wenigen Fingertipps fest, verfolge deine Behandlungen und Laborwerte, bereite deine Untersuchungen vor und bringe einen übersichtlichen Bericht zu deiner Ärztin oder deinem Arzt.

Deine Gesundheitsdaten bleiben auf deinem Smartphone.

<p>
  <img src="../screenshots/home.png" width="200" alt="Heute-Bildschirm">
  <img src="../screenshots/history.png" width="200" alt="Verlaufskalender">
  <img src="../screenshots/trends.png" width="200" alt="Trends">
  <img src="../screenshots/care.png" width="200" alt="Reiter Behandlung">
</p>

## Funktionen

### Tägliches Journal
- **Stuhlgang mit zwei Fingertipps**: Bristol-Skala (Typ 0 = an diesem Tag kein Stuhlgang), Blut, Schleim, Schmerzen und Müdigkeit (0–5), Dringlichkeit, nächtlicher Stuhlgang.
- **Falscher Stuhldrang**: Drang zur Toilette ohne Stuhlgang, bei Schüben häufig. Wird separat erfasst und nie als Stuhlgang gezählt.
- **Mahlzeiten**: Foto oder Barcode-Scan (Produktdetails von Open Food Facts).
- **Abendliche Erinnerung**: eine Benachrichtigung, um Stuhltyp, Schmerzen und Müdigkeit ohne Öffnen der App zu erfassen. Sie bleibt bestehen, bis alle drei Angaben gemacht sind.

<img src="../screenshots/notification.png" width="400" alt="Interaktive abendliche Benachrichtigung">

### Verlauf und Trends
- Monatskalender, eingefärbt nach Stuhlkonsistenz.
- Stuhlgänge pro Tag, 30 Tage / 90 Tage / gesamter Verlauf, Tage mit jeweiligen Beschwerden im Vergleich zum vorherigen Zeitraum.
- **Arztbericht (PDF)**: Zusammenfassung des Zeitraums, Beschwerden, Behandlungen mit Einnahmetreue, letzte Blutbilder mit den Referenzbereichen des Labors.

### Behandlung
- **Behandlungen**: gängige Medikamente bei CED, Erinnerungen, „eingenommen / vergessen“ direkt aus der Benachrichtigung, Einnahmetreue, Warnungen zum Nachbestellen.
- **Blut- und Stuhluntersuchungen**, mit Erinnerungen geplant.
- **Koloskopie und Rektosigmoidoskopie**: Untersuchungstermin, ballaststoffarme Kost und Dosen der Darmvorbereitung wie von deiner Ärztin oder deinem Arzt verordnet, Narkosevorgespräch. Eine Erinnerung für jeden Schritt.
- **Laborergebnisse**: 43 gängige Tests (CRP, fäkales Calprotectin, Blutbild, Eisenstatus, Niere, Leber, Blutfette …), mit dem Referenzbereich, den dein Labor angibt, Diagramme und eine Tabelle der Blutbilder. Die App liefert keine eigenen Normwerte.
- **Laborberichte importieren** (Lab-PDFs, mehrere auf einmal): Der Text des PDFs wird auf dem Smartphone gelesen, Werte werden in die Einheiten der App umgerechnet, und nichts wird gespeichert, bevor du es geprüft hast. Gescannte Dokumente werden nicht unterstützt.

<p>
  <img src="../screenshots/labs.png" width="200" alt="Laborergebnisse nach Test">
  <img src="../screenshots/lab-panels.png" width="200" alt="Tabelle der Blutbilder">
  <img src="../screenshots/lab-chart.png" width="200" alt="CRP-Diagramm mit dem Bereich des Labors">
</p>

### Weitere Funktionen
- Erinnerungen als App-Benachrichtigungen oder als Termine im Telefonkalender.
- Mit Passwort verschlüsselte Sicherung und Wiederherstellung in einer Datei, die du selbst aufbewahrst.
- Geführter Ersteinstieg, helles und dunkles Design, mehrere Farbpaletten.
- Verfügbar auf Englisch, Französisch, Deutsch, Spanisch, Italienisch und Portugiesisch.

## Datenschutz

- Journal, Behandlungen und Laborergebnisse werden in einer lokalen Datenbank auf dem Smartphone gespeichert. Es gibt kein Konto und keinen App-Server.
- Keine Google-Dienste und keine Tracker: Laborberichte werden aus dem eigenen Text des PDFs gelesen (PdfBox-Android) und Barcodes mit ZXing gescannt, beides vollständig auf dem Smartphone.
- Netzwerkzugriff wird nur für Barcode-Abfragen (Open Food Facts) genutzt. Keine Cloud-Sicherung: Die automatische Google-Sicherung von Android ist für die App deaktiviert.
- Exporte sind mit einem Passwort verschlüsselt, das du selbst wählst (AES-256-GCM, Schlüsselableitung mit PBKDF2).
- Die Kalendersynchronisation ist standardmäßig aus. Wenn du sie einschaltest, werden Namen von Behandlungen und Untersuchungen in den Telefonkalender geschrieben, der möglicherweise mit einem Online-Konto synchronisiert wird; die App warnt dich davor, bevor du sie aktivierst.

## Erstellen

Voraussetzungen: Android Studio (aktuell), JDK 17, Android SDK 37. Die App läuft auf Android 8.0 (API 26) und neuer.

```bash
./gradlew :app:installDebug          # build and install on a connected device
./gradlew :shared:testAndroidHostTest # unit tests of the shared module
```

Die Release-Signierung liest `keystore.properties` (nicht im Repository enthalten).

## Projektstruktur

| Modul | Inhalt |
|---|---|
| `shared/` | Kotlin Multiplatform: Datenmodelle, Room-Datenbank, Auswertung, Parser für Laborberichte, Zeitpläne für Erinnerungen und Untersuchungen. Kompiliert für Android und iOS. |
| `app/` | Android-App: Jetpack Compose (Material 3) UI, Hilt, Benachrichtigungen, Kalendersynchronisation, PDF-Bericht, PDF-Textextraktion (PdfBox-Android), Barcode-Scan (ZXing). |

## Haftungsausschluss

IBD Journal ist ein persönliches Tagebuch. Es erfasst und zeigt an, was du eingibst; es deutet deine Ergebnisse nicht und schlägt keine medizinischen Entscheidungen vor, und es ist kein Medizinprodukt. Es ersetzt keine ärztliche Beratung: Befolge immer die Anweisungen deiner Ärztin oder deines Arztes, besonders bei der Vorbereitung auf Untersuchungen und bei Behandlungen.

KI-Werkzeuge wurden zum Schreiben des Codes und zum Erstellen der Bristol-Skala-Symbole verwendet.

## Lizenz

[GNU AGPL-3.0](../../LICENSE). Du darfst die App nutzen, untersuchen, verändern und weitergeben; wer eine veränderte Version verbreitet oder als Online-Dienst anbietet, muss ihren Quellcode unter derselben Lizenz veröffentlichen.
