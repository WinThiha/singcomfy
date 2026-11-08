# 6. Troubleshooting

## The YouTube tab stopped working

Symptoms: search returns nothing, videos say "Couldn't load", or playback stops after a few seconds. The **Live FX** mode isn't affected, because it uses the real YouTube app.

**Why:** the YouTube tab uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor), which reads YouTube the way a browser does. YouTube changes its site every few weeks or months, and NewPipe then publishes a fix.

**Fix:**
1. Check the newest version at https://github.com/TeamNewPipe/NewPipeExtractor/releases (e.g. `v0.27.1`).
2. In `app/build.gradle.kts`, change the version in this line:
   ```kotlin
   implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.27.0")
   ```
3. Build, test the YouTube tab, and publish a new release ([guide 3](03-release.md)).

If the build fails after the update, NewPipe changed something in its code. Check its release notes, or ask Claude with the error message.

If videos still fail, the file `app/src/main/java/com/singcomfy/app/yt/YtDataSource.kt` may need changes. It mimics the official YouTube client when downloading video data.

## A user's phone sound is stuck muted

Live FX mutes the normal media sound while it runs. If the app is force-closed, the next launch restores the sound automatically. If it still happens:

- **User fix:** open SingComfy once and press Start, then Stop. Or raise the media volume in phone settings.
- **Your phone over USB:**
  ```powershell
  adb shell cmd audio adj-unmute 3
  ```
  (3 = the media stream)

The alarm volume may also be left high after a crash. Lower it in phone settings → Sound.

## "Restricted setting" when turning on accessibility

Android 13+ blocks accessibility for apps installed from a download. The user must:
**Settings → Apps → SingComfy → ⋮ (top right) → Allow restricted settings**, then turn on **Accessibility → SingComfy audio output**.

On some phones the ⋮ menu only appears after trying to turn the switch on once.

## The accessibility setting turns itself off

Some phones (Xiaomi, Samsung, Oppo…) turn accessibility services off to save battery, or after an app update. Turn it on again. On aggressive phones, also set **Settings → Apps → SingComfy → Battery → Unrestricted**.

To turn it on for your own phone over USB:

```powershell
adb shell settings put secure enabled_accessibility_services com.singcomfy.app/com.singcomfy.app.live.VolumeA11yService
adb shell settings put secure accessibility_enabled 1
```

This replaces any other enabled accessibility services. Check first with `adb shell settings get secure enabled_accessibility_services`.

## No sound or wrong speaker in Live FX

- Without the accessibility setting, Live FX plays on the **alarm** channel. That one also plays on the phone speaker when headphones or Bluetooth are connected. Turn on the accessibility setting to fix it.
- **Advanced → Output channel** on the home screen lets you force Accessibility, Alarm or Media.
- Some apps block capture: Netflix, Spotify on some phones, banking apps, DRM video. Nothing can process them.

## Play Protect blocks the install

Google doesn't know apps outside the Play Store. Users tap **More details → Install anyway**. If Play Protect ever flags SingComfy as harmful (not just "unrecognized"), report it to Google as a false positive: https://support.google.com/googleplay/android-developer/contact/protectappeal

## The build fails

- `SDK location not found` → create `local.properties` ([guide 1](01-setup.md)).
- `Unsupported class file major version` or Java errors → check `java -version` shows 17 or newer.
- Random errors after updating something → `.\gradlew clean`, then build again.
- `Could not resolve com.github.TeamNewPipe...` → jitpack.io may be down or slow. Wait and retry.
- Release builds signed with the wrong key → `keystore.properties` is missing from the project folder ([guide 4](04-signing-key.md)).

## Someone reports a bug

Users report on https://github.com/WinThiha/singcomfy/issues. Ask for:
- phone model and Android version
- which app they were using (YouTube, Chrome…)
- whether the accessibility setting was on
- what happened vs. what they expected

## Asking Claude for help later

Open Claude Code in `C:\Users\winst\projects\karaokeApp`. It reads the code and these guides. Describe the problem and paste any error. Claude also keeps notes in its memory about the release process and the signing key location.
