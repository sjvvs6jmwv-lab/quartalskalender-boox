# Quartal 2.0.1 — Android-Studio-Quellprojekt für BOOX Note Air 3C (10,3 Zoll)

**Stand:** Quellen zum Gerätetest vorbereitet; keine in dieser Umgebung gebaute APK.

Native Java-/Android-Anwendung ohne WebView. Das vorhandene lokale Datenmodell, JSON-/ICS-Import/Export und 13-Wochen- sowie Quartalsansichten bleiben erhalten. Die 13 Wochen sind für 10,3 Zoll kompakter gerendert. Tagesfelder öffnen die zugehörigen Termine unten; langes Antippen öffnet die Eingabe. Ein neues Eingabeformular bietet `An Kalender`, das die erfassten Inhalte an die Android-Kalender-App übergibt.

**Für Build und Installation** siehe `GERAETETEST_BOox_NOTE_AIR_3C.md`.

**Wichtig:** Ein echtes direktes automatisches Schreiben in den Google-Kalender, die Verarbeitung freier Handschrift und die Live-Bearbeitung von externen Terminen in Quartal sind nicht implementiert. Vorhandene Android-Kalenderereignisse werden über `READ_CALENDAR` gelesen, wenn der jeweilige Kalender im Calendar Provider des Geräts verfügbar ist.
