# Quartal 2.0.1 — BOOX Note Air 3C: Kompilierung und Gerätetest

## Status
Dies ist das **Android-Studio-Quellprojekt**, **keine APK**. Eine APK entsteht erst nach erfolgreicher Kompilierung und Signierung in Android Studio oder GitHub Actions. Die vorhandenen Java-Logiktests (10 Prüfungen) wurden bestanden und beide Java-Quellen syntaktisch geparst. Ein Android-Build und ein Laufzeittest wurden **nicht** durchgeführt.

## App bauen (Mac mit Android Studio)
1. ZIP entpacken; in **Android Studio** über **Open** den Projektordner auswählen (nicht die ZIP-Datei).
2. Eine JDK-17-Laufzeit für Gradle verwenden; Android SDK Platform 35 und Build Tools 35 installieren, falls Android Studio danach fragt.
3. **Sync Project with Gradle Files** abwarten. Benötigt Internet, insbesondere für Gradle 8.9 und Android Gradle Plugin 8.7.3.
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)** ausführen.
5. Nur wenn Android Studio **BUILD SUCCESSFUL** meldet, liegt `app/build/outputs/apk/debug/app-debug.apk` vor. Diese Datei wurde nicht hier erstellt.
6. Per BOOXDrop/USB auf das Note Air 3C übertragen; im BOOX-Dateimanager öffnen und installieren.
7. Falls Google Play Protect die Installation prüft, die Entscheidung am Gerät abwarten. Das ist ein Sicherheits- und **kein Funktionstest**.

## Parallel zur vorhandenen App
Die neue Ausgabe hat die Paket-ID `de.quartal.boox.v2` und das App-Label `Quartal 2`; sie ist damit neben der älteren `de.quartal.boox` installierbar. Bestehende Daten aus 1.5 ggf. zunächst dort als JSON exportieren und in `Quartal 2` importieren. Die bestehende 1.5-App nicht vorher deinstallieren.

## Funktionstest auf dem BOOX
- [ ] 13 ISO-Kalenderwochen gleichzeitig oder im scrollbaren Bereich sichtbar; KW antippen verschiebt das Fenster
- [ ] Tagesfeld antippen zeigt zugehörige Einträge unten; lang antippen öffnet einen neuen Eintrag
- [ ] Terminliste für Tag und 13-Wochen-Zeitraum sichtbar und scrollbar
- [ ] Schrift und rote Punkte auf 10,3 Zoll gut erkennbar
- [ ] Einen Termin lokal anlegen, ändern, löschen; App schließen und erneut öffnen
- [ ] Aus dem Eingabeformular **An Kalender**: Android-Kalender öffnet mit Titel und Zeitpunkt, dort speichern
- [ ] Nach Kalenderspeicherung erneut in Quartal 2 unter Kalender die Quelle anzeigen
- [ ] Bereits im Android-Kalenderspeicher verfügbare Google-Termine erscheinen in der 13-Wochen-Ansicht
- [ ] JSON-Import und -Export vor und nach Neustart

## Bekannte funktionale Grenzen
- Für **An Kalender** wird ein vorausgefüllter Android-Kalenderdialog gestartet. Das Speichern geschieht dort. Es ist **keine automatische bidirektionale Synchronisation lokaler Quartal-Einträge**.
- Externe Termine werden nur angezeigt, wenn der jeweilige Konto-/Kalenderanbieter im systemweiten Android Calendar Provider verfügbar ist.
- Externe Termine werden zur Bearbeitung in der zuständigen Kalender-App geöffnet.
- Echte direkte freie Handschrift im Kalenderfeld mit Handschrifterkennung ist **nicht** implementiert; möglich ist Stifteingabe über die Android-Tastatur/Handschrifteingabe, sofern auf dem Gerät konfiguriert.
- Das Supernote Manta ist keine geprüfte Zielplattform dieser Android-APK.
