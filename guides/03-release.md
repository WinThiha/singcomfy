# 3. Publish a release

A release puts a new version on GitHub. The website's download button and the app's built-in update check both use the newest release automatically.

## Step 1: Raise the version number

Open `app/build.gradle.kts` and find:

```kotlin
versionCode = 2
versionName = "1.1.0"
```

- **versionCode**: add 1 every release (2 → 3). Android refuses to update to a lower or equal number.
- **versionName**: what people see. Use `MAJOR.MINOR.PATCH`:
  - bug fixes only → 1.1.0 → **1.1.1**
  - new features → 1.1.0 → **1.2.0**
  - huge change → **2.0.0**

Also update `"softwareVersion"` in `docs/index.html` to the same versionName.

## Step 2: Build the signed release

```powershell
.\gradlew assembleRelease
```

The result is `app\build\outputs\apk\release\SingComfy-<version>-release.apk`.

**Check it's signed with your real key** (not the debug key):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\37.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\SingComfy-1.2.0-release.apk
```

The `SHA-256 digest` line must start with **`91d1c2775e2f870c`**. If it's different, `keystore.properties` is missing and the build used the debug key: see [guide 4](04-signing-key.md). Never publish a build with a different key, because existing users couldn't update. If your build-tools folder isn't `37.0.0`, check `%LOCALAPPDATA%\Android\Sdk\build-tools` for the number.

## Step 3: Test it

Install it on your phone and go through the checklist in [guide 2](02-build-and-test.md#what-to-test-before-a-release).

## Step 4: Commit and push the code

```powershell
git add -A
git commit -m "Version 1.2.0: <what changed>"
git push
```

## Step 5: Rename the APK

Copy the APK and rename the copy to exactly **`SingComfy.apk`**. Capital S and C, no version number.

```powershell
Copy-Item app\build\outputs\apk\release\SingComfy-1.2.0-release.apk $HOME\.singfx\release\SingComfy.apk -Force
```

## Step 6: Create the release on GitHub

### Option A: GitHub website

1. Go to https://github.com/WinThiha/singcomfy/releases → **Draft a new release**.
2. **Choose a tag** → type `v1.2.0` (a `v` plus the versionName) → **Create new tag**.
3. **Title:** `SingComfy 1.2.0`.
4. **Description:** short notes for users, see the template below. The app shows the first ~600 characters in its update pop-up.
5. Drag in **`SingComfy.apk`**.
6. Leave **"Set as a pre-release"** unticked. Pre-releases are invisible to the download button and to the app.
7. **Publish release**.

### Option B: command line

Write the notes in a file, e.g. `$HOME\.singfx\release\notes-1.2.0.md`, then:

```powershell
gh release create v1.2.0 $HOME\.singfx\release\SingComfy.apk --repo WinThiha/singcomfy --title "SingComfy 1.2.0" --notes-file $HOME\.singfx\release\notes-1.2.0.md --target main
```

### Release notes template

```markdown
**What's new**
- Short, plain sentence per change.

**Fixed**
- What was broken and is now fixed.

**Download:** SingComfy.apk below (Android 10+). Already installed? The app offers the update by itself.
```

## Step 7: Check it worked

- Open https://github.com/WinThiha/singcomfy/releases/latest/download/SingComfy.apk in a browser. It should download the new version.
- In the app: **Advanced → Check for updates** on an older install should offer the new version.

## If you made a mistake

- **Wrong notes:** edit the release on GitHub. Safe at any time.
- **Broken APK:** on the release page, delete the asset and upload a fixed `SingComfy.apk`. If people may already have the broken one, publish a new version with a higher number instead.
- **Never** reuse a versionCode, and never sign with a different key.
