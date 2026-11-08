# 1. Set up a computer

Your current PC already has all of this. Use this guide for a new PC, or when a tool goes missing.

## What to install

| Tool | Why | Get it |
|---|---|---|
| **Android Studio** | Brings the Android SDK, the emulator and `adb` | https://developer.android.com/studio |
| **JDK 17 or newer** | Runs the build. This PC uses Liberica JDK 21 | https://bell-sw.com/pages/downloads/ (or use the one inside Android Studio) |
| **Git** | Downloads and uploads code | https://git-scm.com |
| **GitHub CLI** (`gh`), optional | Publishes releases from the command line | https://cli.github.com |
| **ffmpeg**, optional | Only for regenerating the website's preview image | https://www.gyan.dev/ffmpeg/builds/ |

After installing Android Studio, open it once and let it finish downloading the SDK. In **Settings → Languages & Frameworks → Android SDK**:
- under **SDK Platforms**, tick **Android 16 (API 36)**
- under **SDK Tools**, tick **Android SDK Build-Tools**, **Platform-Tools** and **Android Emulator**

## Get the code

```powershell
cd $HOME\projects
git clone git@github.com:WinThiha/singcomfy.git karaokeApp
cd karaokeApp
```

If `git@github.com` fails, use `https://github.com/WinThiha/singcomfy.git` instead, or sign in with `gh auth login` first.

## Two local files (never committed)

**`local.properties`** in the project folder tells the build where the Android SDK is:

```
sdk.dir=C:/Users/winst/AppData/Local/Android/Sdk
```

Use forward slashes. Android Studio creates this file for you if you open the project there.

**`keystore.properties`** in the project folder lets release builds be signed. Copy it from your key backup:

```powershell
Copy-Item $HOME\.singfx\keystore.properties .\keystore.properties
```

On a new PC, restore the whole `.singfx` folder from your backup first (see [guide 4](04-signing-key.md)). If `storeFile` in that file points to another path, edit it.

## Make `adb` work in any terminal

`adb` is in `C:\Users\winst\AppData\Local\Android\Sdk\platform-tools`. Add that folder to your **PATH**: Start → "Edit the system environment variables" → Environment Variables → Path → New. Then open a new terminal and run:

```powershell
adb version
```

## Check everything works

```powershell
.\gradlew assembleDebug
```

The first run downloads a lot and takes a few minutes. It should end with `BUILD SUCCESSFUL`.
