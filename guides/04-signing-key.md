# 4. The signing key

Every release is signed with your private key. Android only installs an update when it's signed with the **same** key as the installed app.

> **If the key is lost, nobody can update SingComfy anymore.** You'd have to publish a new app under a new ID, and every user would have to uninstall and reinstall. Back it up.

## Where it is

```
C:\Users\winst\.singfx\
├── singfx-release.jks       ← the key itself (the name says singfx, that's fine)
├── keystore.properties      ← passwords and settings for the build
└── release\                 ← staged APKs and release notes (not secret)
```

The project folder also has a copy of `keystore.properties`. Git ignores it, so it never goes to GitHub. Never commit it, and never upload the `.jks` file anywhere public.

`keystore.properties` looks like this:

```
storeFile=C:/Users/winst/.singfx/singfx-release.jks
storePassword=********
keyAlias=singfx
keyPassword=********
```

- **Key fingerprint (SHA-256):** starts with `91:D1:C2:77`. Full value: `91d1c2775e2f870c72adb9c4d8b5cfbeac1e4dfcc39ecc159cbda86cdb838999`.
- Key type: RSA 4096, valid for 100 years.

## Back it up (do this now)

Keep **both** `singfx-release.jks` and `keystore.properties` in at least two of these places:

1. A USB stick kept somewhere safe.
2. A private cloud folder: Google Drive, OneDrive (not a shared folder).
3. A password manager that stores files (1Password, Bitwarden). Also save the two passwords there as text.

Test a backup once: copy it to another folder, point `storeFile` at it, run `.\gradlew assembleRelease`, and check the fingerprint (see [guide 3](03-release.md#step-2-build-the-signed-release)).

## Set it up on a new PC

1. Copy the `.singfx` folder from your backup to `C:\Users\<you>\.singfx\`.
2. If the path changed, edit `storeFile` in `keystore.properties`. Use forward slashes.
3. Copy `keystore.properties` into the project folder.
4. Build a release and check the fingerprint starts with `91d1c2775e2f870c`.

## How the build uses it

`app/build.gradle.kts` reads `keystore.properties` from the project folder:
- **File present:** release builds are signed with your key.
- **File missing:** release builds are signed with a throwaway debug key. Fine for testing, but **never publish them**.
- `.\gradlew assembleRelease -PsignWithDebug` forces the debug key on purpose (testing only).

## Play Store note

If you later publish on Google Play, use **Play App Signing** and upload with this same key. Google keeps its own copy, which protects you against losing it. See [guide 7](07-play-store.md).
