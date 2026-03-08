# Kimi Voice - Android Sprachassistent

Ein Voice-Activated Assistant für Android, der auf das "Hey Kimi" Wake Word reagiert und mit OpenClaw kommuniziert.

## Features

- **Wake Word Detection**: Reagiert auf "Hey Kimi", "Ey Kimi" oder "Kimi"
- **Spracherkennung**: Offline Spracherkennung für Befehle  
- **Premium TTS**: ElevenLabs Cloud-Sprache oder Google TTS
- **System-Integration**: Öffnet Apps, navigiert, spielt Musik
- **OpenClaw API**: Sendet Befehle an deinen OpenClaw Gateway

## Unterstützte Befehle

| Befehl | Aktion |
|--------|--------|
| "Navigier nach Stuttgart" | Öffnet Google Maps mit Route |
| "Spiel Musik auf Spotify" | Öffnet Spotify |
| "Ruf Mama an" | Öffnet Telefon-App |
| "Wecker auf 7 Uhr" | Setzt Wecker |
| "Timer für 5 Minuten" | Startet Timer |
| "Öffne Kalender" | Öffnet Kalender |
| "Öffne Kamera" | Startet Kamera |
| "Öffne YouTube" | Öffnet YouTube |

## Setup

### 1. Voraussetzungen

- Android Studio Hedgehog (2023.1.1) oder neuer
- Android SDK 34
- Kotlin 1.9+

### 2. Wake Word Datei

Die `.ppn` Datei ist bereits im Projekt (`app/src/main/assets/hey-kimi.ppn`).

### 3. OpenClaw Gateway konfigurieren

1. Öffne die App
2. Gib deine OpenClaw Gateway URL ein (z.B. `http://192.168.1.100:8080`)
3. Optional: Füge deinen Gateway Token hinzu
4. Tippe auf "Verbindung testen"

### 4. TTS (Text-to-Speech) einrichten

Die App unterstützt **drei Stimm-Optionen**:

#### Option A: ElevenLabs (⭐ Beste Qualität)

**Klingt wie ein echter Mensch** - fast nicht von echter Stimme zu unterscheiden.

1. Gehe zu [elevenlabs.io](https://elevenlabs.io)
2. Erstelle einen Account (kostenlos: 10k Zeichen/Monat)
3. Kopiere deinen API Key
4. Trage ihn in den App-Einstellungen ein

**Kosten:** ~$5/Monat für 100k Zeichen (mehr als genug)

#### Option B: Google TTS (Kostenlos, Gut)

**Deutlich besser als Standard-Android-TTS**.

1. Installiere "Google Text-to-Speech" aus dem Play Store
2. Gehe zu Android Einstellungen → Sprache & Eingabe → Text-in-Sprache
3. Wähle "Google" als bevorzugte Engine
4. Lade deutsche Sprachdaten herunter

#### Option C: System TTS (Kostenlos, Standard)

Funktioniert sofort, klingt aber sehr roboterhaft.

### 5. Berechtigungen

Die App benötigt folgende Berechtigungen:

- **Mikrofon**: Für Wake Word und Spracherkennung
- **Overlay**: Für schwebende Antwort-Anzeige
- **Benachrichtigungen**: Für Foreground Service
- **Internet**: Für ElevenLabs TTS und OpenClaw API
- **Akku-Optimierung**: Muss deaktiviert werden für Hintergrund-Betrieb

### 6. Build & Install

```bash
# Debug-Build
./gradlew assembleDebug

# Oder direkt auf Gerät installieren
./gradlew installDebug
```

## TTS Qualitäts-Vergleich

| Option | Qualität | Kosten | Offline | Latenz |
|--------|----------|--------|---------|--------|
| **ElevenLabs** | ⭐⭐⭐⭐⭐ Menschlich | $5/Monat | ❌ | ~1s |
| **Google TTS** | ⭐⭐⭐⭐ Sehr gut | Kostenlos | ✅ | Sofort |
| **System TTS** | ⭐⭐ Robotisch | Kostenlos | ✅ | Sofort |

**Empfehlung:** Nutze ElevenLabs für das volle Erlebnis, Google TTS als kostenlose Alternative.

## Architektur

```
┌─────────────────┐     ┌──────────────┐     ┌─────────────────┐
│  Porcupine      │────▶│  HTTP Client │────▶│  OpenClaw       │
│  Wake Word      │     │  (Befehl)    │     │  Gateway        │
└─────────────────┘     └──────────────┘     └─────────────────┘
                                                        │
┌─────────────────┐     ┌──────────────┐             │
│  Intent/Action  │◄────│  Hybrid TTS  │◄────────────┘
│  (Maps, etc.)   │     │  (Sprache)   │
└─────────────────┘     └──────────────┘
```

## Dateistruktur

```
kimi-voice-android/
├── app/src/main/java/com/kimi/voice/
│   ├── MainActivity.kt                    # Haupt-UI
│   ├── api/
│   │   ├── OpenClawApi.kt                 # OpenClaw Client
│   │   └── ElevenLabsTts.kt               # Premium TTS
│   ├── service/
│   │   ├── SimpleVoiceService.kt          # Ohne Porcupine
│   │   └── WakeWordService.kt             # Mit Porcupine
│   └── util/
│       ├── IntentHandler.kt               # System-Intents
│       ├── TtsManager.kt                  # Local TTS
│       └── HybridTtsManager.kt            # TTS Routing
├── app/src/main/assets/
│   └── hey-kimi.ppn                       # Dein Wake Word
└── README.md
```

## Stimm-Beispiele

### ElevenLabs (Bella Voice)
> "Starte Navigation nach Stuttgart. Die Fahrt dauert etwa 2 Stunden."

Natürlich, warm, menschliche Intonation.

### Google TTS (Deutsch)
> "Starte Navigation nach Stuttgart."

Klar, verständlich, leicht synthetisch aber angenehm.

### System TTS
> "STARTE NAVIGATION NACH STUTTGART"

Roboter-Stimme, sehr synthetisch.

## Troubleshooting

### Service startet nicht
- Prüfe Berechtigungen (Mikrofon, Overlay)
- Akku-Optimierung deaktivieren
- App im "Autostart" erlauben

### Keine Verbindung zu OpenClaw
- Gateway URL prüfen (mit `http://`)
- Gleiches Netzwerk? (Port-Forwarding für externen Zugriff)
- Firewall-Regeln prüfen

### Wake Word wird nicht erkannt
- Mikrofon-Berechtigung erteilt?
- In ruhiger Umgebung testen
- Alternativ: `SimpleVoiceService` nutzen (ohne Porcupine)

### TTS funktioniert nicht
- **ElevenLabs**: API Key korrekt? Internet-Verbindung?
- **Google TTS**: Sprachdaten heruntergeladen?
- **System TTS**: Immer als Fallback verfügbar

### Stimme klingt komisch
- Wechsle zu ElevenLabs für beste Qualität
- Oder installiere Google TTS aus dem Play Store
- In App-Einstellungen TTS-Modus ändern

## Nächste Schritte

- [x] Custom Wake Word ("Hey Kimi")
- [x] Text-to-Speech mit ElevenLabs
- [ ] Floating UI für Antworten
- [ ] Widget für schnellen Zugriff
- [ ] Auto-start bei Boot

## Lizenz

Privates Projekt für Max ❤️‍🔥# Build Status
