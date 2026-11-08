# Metronome (Android)

Native Android app (Kotlin + Jetpack Compose), minSdk 26 (Android 8+).

**Metronome:** 30-300 BPM, tap tempo, 1-12 beats per bar, subdivisions (eighths, triplets, sixteenths), 6 synthesised sounds with preview, per-beat vibration (stronger on beat 1), volume.
**Tuner:** microphone pitch detection (YIN) with a cents needle and adjustable A4 reference (415-466 Hz).
**Drone:** any of the 12 notes in octaves 1-6, three tones (Sine, Warm, Organ), volume.
**Background play:** a foreground service keeps the metronome and drone going in other apps, with a Stop button in the notification. Switch it off under "Keep playing in other apps" and sound stops when you leave the app.
**Themes:** System / Light / Dark / AMOLED plus six accent colours, in a One UI style (large title, rounded cards) with frosted-glass touches.

## Get the APK

### Option A: GitHub Actions (no installs)
1. Create a new GitHub repository and push this folder to it (branch `main`).
2. Open the **Actions** tab, then **Build APK**, then the latest run.
3. Download the `metronome-apk` artifact, unzip it, and copy `app-debug.apk` to your phone.
4. Open it on the phone and allow "install unknown apps" when asked.

### Option B: Android Studio
1. Open this folder in Android Studio (Koala or newer) and let Gradle sync.
2. **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
3. The file is at `app/build/outputs/apk/debug/app-debug.apk`.

### Option C: command line
With JDK 17 and the Android SDK installed: `./gradlew assembleDebug`

## Notes
- Android asks for notification permission (so the Stop button shows) and microphone permission (tuner only).
- The tuner listens to the mic only while it is on screen.
- Beat vibration and the audible click are scheduled together, but speaker latency varies by phone, so the buzz can land slightly ahead of the sound.
