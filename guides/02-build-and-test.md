# 2. Build and test

Run all commands in a terminal **inside the project folder** (`C:\Users\winst\projects\karaokeApp`). These are PowerShell commands. In Git Bash, write `./gradlew` instead of `.\gradlew`.

## Easiest way: Android Studio

1. **File → Open** the project folder.
2. Plug in your phone (see below) or start the emulator.
3. Pick the device at the top and press the green ▶ **Run** button.

This installs a **debug** build. That's fine for testing.

## Build from the command line

| Command | Result |
|---|---|
| `.\gradlew assembleDebug` | Test build: `app\build\outputs\apk\debug\SingComfy-<version>-debug.apk` |
| `.\gradlew assembleRelease` | Real build for users, signed with your key: `app\build\outputs\apk\release\SingComfy-<version>-release.apk` |
| `.\gradlew clean` | Deletes old build files. Try this when a build acts strangely |

A release build takes 2–4 minutes because it shrinks and optimizes the code.

## Connect your phone

1. On the phone: **Settings → About phone**, tap **Build number** 7 times. This turns on Developer options.
2. **Settings → System → Developer options → USB debugging**: on.
3. Plug in the USB cable and tap **Allow** on the phone.
4. Check with:

```powershell
adb devices
```

You should see your phone with `device` next to it. `unauthorized` means you still need to tap Allow on the phone.

## Install on the phone

```powershell
adb install -r app\build\outputs\apk\release\SingComfy-1.1.0-release.apk
```

`-r` updates the app and keeps its settings.

**"INSTALL_FAILED_UPDATE_INCOMPATIBLE"** means the phone has a build signed with a different key. This happens when switching between debug and release builds. Uninstall first, then install again:

```powershell
adb uninstall com.singcomfy.app
```

After a fresh install, turn on **Settings → Accessibility → SingComfy audio output** again.

## Use the emulator (no phone needed)

In Android Studio: **Device Manager → Medium_Phone → ▶**. Or:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd Medium_Phone
```

The emulator sometimes runs out of memory and closes the app on its own. Just open the app again.

## What to test before a release

- [ ] The app opens on the **Live FX** home screen.
- [ ] **Start** → allow screen sharing → "Live FX is on" appears with the floating bubble.
- [ ] Play something in YouTube. Change the key in the bubble and you hear the change.
- [ ] Phone volume buttons change the karaoke volume while Live FX is on.
- [ ] Drag the bubble around, open it, and check the panel stays fully on screen.
- [ ] **Stop**, then check normal YouTube sound comes back.
- [ ] **YouTube** tab: search a song, play it, change the key.
- [ ] **Library** tab: play a song from the phone.

## See error logs

When something crashes, plug the phone in and run:

```powershell
adb logcat -d | Select-String "AndroidRuntime|singcomfy|LiveFx" | Select-Object -Last 60
```

Paste the output to Claude or into a GitHub issue.
