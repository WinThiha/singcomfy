# Maintainer guides

How to keep SingComfy running on your own: building, releasing, the website, Google, and fixing common problems.

| Guide | When you need it |
|---|---|
| [1. Set up a computer](01-setup.md) | New PC, or something is missing |
| [2. Build and test](02-build-and-test.md) | You changed something and want to try it on a phone |
| [3. Publish a release](03-release.md) | You want users to get a new version |
| [4. The signing key](04-signing-key.md) | Backups, a new PC, or the key seems lost |
| [5. Website and Google](05-website-and-google.md) | Editing the site, screenshots, search ranking |
| [6. Troubleshooting](06-troubleshooting.md) | YouTube stopped working, sound stuck muted, setup problems |
| [7. Play Store, later](07-play-store.md) | When you get a Google Play developer account |

## Where everything lives

| What | Where |
|---|---|
| Code | This repo: `github.com/WinThiha/singcomfy` (local copy: `C:\Users\winst\projects\karaokeApp`) |
| Website | The `docs/` folder. GitHub Pages publishes it at https://winthiha.github.io/singcomfy/ |
| Downloads | GitHub Releases. The site's download button always gets the newest `SingComfy.apk` |
| Signing key | `C:\Users\winst\.singfx\` (outside the repo, never on GitHub) |
| App ID | `com.singcomfy.app` (old SingFX 1.0.0 was `com.singfx.app`) |

## The three rules

1. **Never lose the signing key** (`C:\Users\winst\.singfx\`). Without it, nobody can update their installed app. Back it up now: see [guide 4](04-signing-key.md).
2. **The release file must be named exactly `SingComfy.apk`.** The website's download button depends on that name.
3. **Never delete `docs/googlebeee7b6461b76bfe.html`.** It proves to Google that you own the website.

## Glossary

- **APK**: the app file people install.
- **adb**: a command-line tool that talks to a phone over USB (install apps, read logs).
- **Gradle / `gradlew`**: the build tool. `gradlew` is in the repo, so you don't install it.
- **Release build**: the optimized, signed APK you give to users. **Debug build**: for testing only.
- **versionCode / versionName**: the app's internal number (1, 2, 3…) and the version people see (1.1.0).
