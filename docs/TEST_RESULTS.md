# Verification record

Date: 2026-10-26. No real provider credentials were used. These results distinguish
automated behavior from physical TV and media qualification.

| Check | Status |
|---|---|
| JVM unit tests | **32 passed**, zero failures/errors/skips |
| Final full build | **Passed**, 87 tasks; tests, lint, debug app and test APKs |
| Android lint | **Passed**, 0 errors; 17 warnings |
| Android TV emulator instrumentation | **12 passed**, zero failures/skips; API 36 x86_64, WHPX |
| Animations disabled | **7 input/navigation cases passed** again; zero failures/skips |
| APK installation / Leanback launcher | Installed; launcher resolved on the emulator |
| Native alignment | Four ELF64 libraries and all eight ZIP entries pass 16 KiB alignment inspection |
| Physical TV / vendor keyboards and remotes | Not available; testing still required |
| Real provider / video decoding | Not tested; authorized media/accounts required |
| Performance, standby and four-hour soak | Not measured on physical hardware |
| 16 KiB page-size runtime | Not tested |

The final Windows build used `scripts/build.ps1` (JDK 17, Gradle 8.13, SDK 36):
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.
It completed successfully in 5 minutes after the final source changes.

Source repository preparation also verified the official Gradle wrapper checksum
and built a fresh isolated checkout through `gradlew.bat` using an external SDK
and JDK. All 32 JVM tests, lint (0 errors, 17 warnings) and both debug APK builds
passed. Toolchains, local SDK paths, generated outputs and raw logs are excluded
from the source commit; the Unix wrapper is tracked as executable with LF endings.

JVM coverage includes M3U normalization and relative HTTP stream/logo URLs,
stable identities, Xtream HTTP URL construction and authentication variants,
XMLTV dates/DTD refusal, PIN hashing, secret redaction, 10k catalogue/search
behavior, bounded responses/manifests, HTTP clients, same-host TLS upgrades,
HTTPS downgrade/cross-host redirect refusal, cancellation and untrusted
certificate rejection. Generated JVM reports stay local and are excluded from Git.

The final emulator suite completed with `OK (12 tests)` in 125.070 seconds.
The recorded coverage includes:

- Silent focus for text, URI, username, password and numeric PIN inputs;
  D-pad movement, explicit OK/Enter activation, Done/Back, retained values/focus,
  touch input and password semantics.
- A long scrollable dialog whose last field must remain fully visible above
  a floating TV keyboard, followed by correct keyboard/dialog Back ordering.
- Invalid M3U URL and missing Xtream username focusing the correct field without
  entering edit mode, plus welcome/dialog focus restoration.
- Real Android cleartext policy and production HTTP clients for M3U, Xtream,
  XMLTV and logo requests; actual Media3 HLS manifest/segment fetching.
- Browser search/favorites/playback entry/return focus, Keystore authentication,
  encrypted storage, atomic refresh, restrictions, PIN attempt delay and deletion.
- 100 rapid mocked channel switches using one player, then release/cancellation.

The same seven input/navigation cases passed with system animator duration scale
set to zero: `OK (7 tests)`, 44.363 seconds. The previous emulator setting was
restored. Raw instrumentation output stays local and is excluded from Git.

The HTTP HLS segment contains deliberately invalid synthetic bytes. These tests
prove transport and lifecycle behavior; they do not render real video or qualify
provider compatibility, decoders, codecs or physical playback performance.

The shared dialog handles a TV keyboard that reports visibility with a zero-height
inset by using an editor in the upper half of the parent window. Active fields
scroll into view after the viewport changes. Normal IMEs use their reported
insets. See the [manual test procedure](TESTING.md) for vendor keyboard,
remote, low-powered TV, real HTTP/HTTPS media and animation-setting acceptance.

Debug APK from the UX verification run (not committed):
`app/build/outputs/apk/debug/app-debug.apk`, 45,987,883 bytes.
SHA-256: `2feeb34238d87850cbe0f1f21ede63af805f269ff92d22642664b85c0bc39560`.
Native alignment was checked with `python scripts/inspect-apk.py`. The generated
inspection output stays local and is excluded from Git. Rebuilds can produce a
different debug APK checksum.

Emulator screenshots use synthetic data only:
[remote input focus](screenshots/source-remote-focus.png),
[keyboard editing](screenshots/source-keyboard.png),
[welcome](screenshots/welcome.png) and [browser](screenshots/browser.png).

Lint warnings concern 14 dependency/version notices, TV landscape orientation,
launcher density assets and the intentional base cleartext allowance. Runtime
provider hosts cannot be declared in a fixed domain allowlist; the shared client
still validates HTTP(S) URLs and redirect destinations, and HTTPS retains normal
system certificate validation. No lint baseline was added.
