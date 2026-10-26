# Verification record

Date: 2026-10-26. No real provider credentials were used. These results distinguish
automated behavior from physical TV and media qualification.

| Check | Status |
|---|---|
| JVM unit tests | **32 passed**, zero failures/errors/skips |
| Final full build | **Passed**, 87 tasks; tests, lint, debug app and test APKs |
| Android lint | **Passed**, 0 errors; 17 warnings |
| Android TV emulator instrumentation | **15 passed**, zero failures/skips; API 36 x86_64, WHPX |
| Offline icons / animations disabled | **3 icon cases passed** again with app networking blocked and animator scale zero |
| Earlier input verification with animations disabled | **7 input/navigation cases passed**, zero failures/skips |
| Font Awesome packaging | All **39** local vectors load; upstream license bundled and accessible offline |
| APK installation / Leanback launcher | Installed; launcher resolved on the emulator |
| Native alignment | Four ELF64 libraries and all eight ZIP entries pass 16 KiB alignment inspection |
| Physical TV / vendor keyboards and remotes | Not available; testing still required |
| Real provider / video decoding | Not tested; authorized media/accounts required |
| Performance, standby and four-hour soak | Not measured on physical hardware |
| 16 KiB page-size runtime | Not tested |

The final Windows build used `gradlew.bat` (JDK 17, Gradle 8.13, SDK 36):
`testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.
It completed successfully in 5 minutes 10 seconds after the final icon changes:
87 tasks, 17 executed and 70 up to date.

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

The final emulator suite completed with `OK (15 tests)` in 102.207 seconds.
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
- All 39 Font Awesome vectors load as square Android `VectorDrawable` resources;
  the packaged license contains its attribution and CC BY 4.0 terms.
- Labeled and icon-only controls preserve accessible names, selected/disabled
  semantics, D-pad focus and single Enter activation; disabled actions stay inert.
- The local license reader scrolls by remote buttons, disables unavailable page
  actions and restores focus to its launcher on Back.

The three icon cases passed again in 7.291 seconds with the Android restricted
networking mode enabled and system animator duration scale set to zero. The
target app UID's effective network policy was `RESTRICTED_MODE`, confirming app
network access was blocked. The license and all icon resources still loaded;
both system settings were restored afterward. ZIP inspection confirms the debug
APK contains all 39 compiled vectors and the exact bundled upstream license.
There is no Font Awesome font, CDN or added runtime dependency.

Before the icon integration, seven input/navigation cases passed with system animator duration scale
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

Debug APK from the final icon verification run (not committed):
`app/build/outputs/apk/debug/app-debug.apk`, 46,449,102 bytes.
SHA-256: `3c71544aceffc00c8a40c87e5fb7a2d89d4f7c43617d8dd312e288a0438c2000`.
Native alignment was checked with `python scripts/inspect-apk.py`. The generated
inspection output stays local and is excluded from Git. Rebuilds can produce a
different debug APK checksum.

Emulator screenshots use synthetic data only:
[remote input focus](screenshots/source-remote-focus.png),
[keyboard editing](screenshots/source-keyboard.png),
[welcome](screenshots/welcome.png), [browser](screenshots/browser.png),
[icon focus/selection/disabled states](screenshots/icons-states.png) and
[pressed icon control](screenshots/icons-pressed.png). The earlier keyboard
editing capture predates the icon update; the other captures show the new icons.

Physical TV checks still include icon readability at normal viewing distance,
720p/1080p/4K and large text, TalkBack names, RTL layouts, vendor remote focus,
display contrast and responsiveness on lower-powered TVs. These were not
qualified by the emulator. See [Font Awesome attribution](../THIRD_PARTY_NOTICES.md)
and the [manual procedure](TESTING.md).

Lint warnings concern 14 dependency/version notices, TV landscape orientation,
launcher density assets and the intentional base cleartext allowance. Runtime
provider hosts cannot be declared in a fixed domain allowlist; the shared client
still validates HTTP(S) URLs and redirect destinations, and HTTPS retains normal
system certificate validation. No lint baseline was added.
