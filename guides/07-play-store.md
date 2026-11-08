# 7. Play Store, later

SingComfy isn't on Google Play yet. This is what it will take, so you can plan.

## Account

1. Create a developer account at https://play.google.com/console ($25 one-time).
2. Verify your identity. New **personal** accounts must run a **closed test with at least 12 testers for 14 days** before going public. Organization accounts skip this, but need a D-U-N-S number.

## Changes the app needs first

| Problem | Why Play would reject it | What to do |
|---|---|---|
| **YouTube tab** (NewPipe) | Play bans apps that download or stream YouTube outside YouTube's official player | Remove the YouTube tab from the Play version. Keep it in the GitHub version. Live FX with the real YouTube app is fine |
| **Accessibility service** | Play requires a declaration and only allows it for clearly explained uses | Fill in the Accessibility declaration in Play Console, plus a short video showing the volume-button feature. Explain it only handles volume keys and the audio channel |
| **Update checker** | Play apps must update through Play | Turn off `UpdateChecker` in the Play version |
| **Screen capture** | Allowed, but needs a clear purpose | Describe it as "audio playback capture for karaoke effects" in the listing and the Data safety form |

The cleanest way is two **build flavors**: `github` (everything) and `play` (no YouTube tab, no update checker), built from the same code.

## Upload

- Play needs an **AAB** file, not an APK: `.\gradlew bundlePlayRelease` (after adding flavors) or `.\gradlew bundleRelease`.
- Turn on **Play App Signing** and upload with your existing key ([guide 4](04-signing-key.md)). Google then keeps a safe copy.
- The app ID `com.singcomfy.app` can stay the same. If the Play version is signed by Google's key, Play and GitHub installs can't update each other, so users pick one.

## Store listing checklist

- [ ] App name: SingComfy. Short description (80 characters), e.g. "Change the key of any song. Karaoke key changer, vocal remover and EQ."
- [ ] Full description (reuse the website text)
- [ ] Icon 512×512, feature graphic 1024×500, at least 2 phone screenshots
- [ ] Privacy policy URL: https://winthiha.github.io/singcomfy/privacy.html
- [ ] Data safety form: no data collected, no data shared
- [ ] Content rating questionnaire
- [ ] Accessibility declaration (see above)

Ask Claude to do the flavor split when you're ready. It's about an hour of work.
