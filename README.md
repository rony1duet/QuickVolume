# QuickVolume

An Android app (Kotlin) for controlling system volume and ringer mode without
working hardware volume buttons.

## What's included

1. **Main app (`MainActivity`)** — sliders for Media, Ring, Alarm and
   Notification volume, buttons to switch Normal / Vibrate / Silent, and a
   toggle to turn the floating bubble on/off.
2. **Home screen widget (`RingerModeWidgetProvider`)** — a small widget with
   Normal / Vibrate / Silent buttons plus Vol+ / Vol− for media volume, for
   one-tap control without opening the app.
3. **Floating bubble (`FloatingVolumeService`)** — a small draggable overlay
   button that stays on top of every app (and works from the lock screen
   background). Tap the top half for volume up, bottom half for volume down.
   This is the closest thing to actually replacing your broken side button.

## How to build

1. Open this folder (`QuickVolume/`) in **Android Studio** (Hedgehog or newer).
   Android Studio will generate the Gradle wrapper automatically on first
   sync — you don't need to add it manually.
2. Let Gradle sync, then click **Run** on a connected device or emulator
   (minSdk 24 / Android 7.0+).

If you'd rather build from the command line, run `gradle wrapper` once inside
this folder to generate `gradlew`, then `./gradlew installDebug`.

## Permissions you'll be asked for on first run

- **Do Not Disturb access** — required by Android to switch ringer mode
  (Normal/Vibrate/Silent) from an app. The app shows an orange card with a
  button that jumps you straight to the right settings screen.
- **Display over other apps** — required for the floating bubble. Toggling
  "Show floating volume bubble" in the app will prompt for this if not
  already granted.

## Adding the widget to your home screen

Long-press your home screen → **Widgets** → find **QuickVolume Controls** →
drag it onto your home screen.

## Notes

- Volume sliders use the standard `AudioManager` stream APIs
  (`STREAM_MUSIC`, `STREAM_RING`, `STREAM_ALARM`, `STREAM_NOTIFICATION`).
- The bubble runs as a foreground service with a low-priority persistent
  notification (required by Android for services that stay alive), so it
  survives even when the app itself isn't open.
- All package names use `com.quick.volume` — rename via Android
  Studio's refactor tool if you plan to publish this.
