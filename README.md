# EZZY

A private, offline vault for the things you always need in a hurry — bank details, CNIC and
passport numbers, receipts, warranties, contacts, screenshots — reachable from **inside any
other app** through a floating bar.

Everything lives on the phone. There is no account, no cloud, and the app has no `INTERNET`
permission at all.

---

## What it does

**Store, in your own structure.** You create sections (Bank & Cards, Documents, Warranty…) and
pick an entry type. A type is a template — Bank Account, Debit/Credit Card, Document/ID,
Affidavit/Legal, Receipt, Warranty, Contact, Login, Vehicle, Wi-Fi, Screenshot, Free Form — and
it pre-fills the right fields so the second entry takes seconds, not minutes. Every field can be
renamed, retyped, reordered or removed, and you can build your own types from scratch.

**Get it back fast.** Swipe up with two fingers (or tap the floating button) and the bar appears
over whatever app you are in: a rail of your section icons on the right, entries beside it, and
every value with its own copy button.

**Field types that matter.** Text, long text, secret (masked until you tap the eye), number,
phone, email, website, and date with a picker.

**Files.** Photos, scans and PDFs attach to an entry and are sealed on disk — never in your
gallery.

## How it is kept safe

| | |
|---|---|
| Database | SQLCipher, with a random passphrase sealed by an AES-256-GCM key in the Android Keystore |
| Attachments | Each file encrypted with its own Keystore-held key, inside app-private storage |
| Unlock | Fingerprint / face / device PIN, with a configurable re-lock delay |
| Clipboard | Copies are marked sensitive (hidden from the Android 13+ preview) and wiped after a timeout |
| Screen | `FLAG_SECURE` blocks screenshots and screen recording while EZZY is on screen |
| Backup | Excluded from cloud backup and device transfer — the key cannot leave the device anyway |
| Network | No `INTERNET` permission in the manifest |

## The floating bar

Android does not let one app read touches meant for another, so a system-wide gesture is only
possible inside a window EZZY owns. Two triggers are provided:

- **Floating button** — a draggable bubble. Always works, snaps to the nearest edge.
- **Edge swipe** — an invisible strip along the bottom (default, sitting clear of the system's
  own gesture area), right or left edge. Two-finger swipe up by default, so a stray one-finger
  swipe never opens it.

Both are backed by a foreground service, which is why there is an ongoing notification.

> Xiaomi, Oppo, Vivo and Realme kill background services aggressively. If the bar disappears,
> allow EZZY to autostart and exclude it from battery optimisation.

## Build

```bash
./gradlew assembleDebug
```

Requires the Android SDK (compileSdk 35) and JDK 17. Output: `app/build/outputs/apk/debug/`.

- Kotlin 2.0.21 · Jetpack Compose (Material 3) · Room + SQLCipher · DataStore · Biometric
- `minSdk` 26, `targetSdk` 35
- No DI framework — a small hand-written `AppContainer` service locator (`EzzyApp.kt`)

## Layout

```
data/     entities, DAOs, encrypted database, Keystore crypto, attachment store, repository
ui/       theme, icon catalog, shared components, screens, navigation
overlay/  foreground service, window hosts, edge gesture detector, the floating bar UI
security/ app lock, biometric gate, self-clearing clipboard
util/     settings (DataStore)
```
