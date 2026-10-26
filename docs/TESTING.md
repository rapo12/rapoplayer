# Test procedures

## Automated

`gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`

JVM fixtures cover M3U BOM/Unicode/relative URLs/malformed input, duplicate and
stable identities, a 10,000-channel import and search, Xtream response variants,
XMLTV DST/matching/DTD refusal, PIN hashing, secret redaction, bounded retry,
HTTP/HTTPS URL validation, relative HTTP media/logo URLs, same-host TLS upgrades,
redirect downgrade refusal, normal certificate validation, size limits, HTTP
classification and cancellation.

On a clean TV emulator or device:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.rapoplayer.tv.test/androidx.test.runner.AndroidJUnitRunner
```

Alternatively run `gradlew connectedDebugAndroidTest`. NavigationTest assumes
first launch. DeviceRegressionTest uses isolated in-memory Room, synthetic HTTPS
responses and a test-only trusted certificate. It checks atomic refresh failure,
stable favorites, encrypted columns, Keystore authentication, source deletion,
PIN retention and restrictions enforced at repository playback entry points.
BrowserJourneyTest installs and removes its own encrypted synthetic catalogue in
the application database and skips if a user source or PIN already exists.
Run against a dedicated test install. PlayerLifecycleTest switches one player
100 times against a local HTTPS error fixture and verifies release/cancellation.
InputBehaviorTest exercises all input types with silent remote focus, explicit
OK/Enter activation, Done/Back, value retention, touch activation and password
semantics. HttpSourceFlowTest uses real Android cleartext policy and the production
network clients for M3U, Xtream, XMLTV, logos and Media3 HLS segment requests.
Its synthetic segment deliberately contains no decodable media; transport success
does not establish video compatibility.
NavigationTest also checks invalid M3U and Xtream fields receive focus without
entering edit mode. The dialog input test checks screen position for floating TV
keyboards that report a zero-height inset, as well as Back ordering. It uses a
long scrollable form and requires the last input to be fully visible above the
floating keyboard after the dialog resizes.

IconUiTest loads every bundled Font Awesome vector through Android resources,
checks the local license, and exercises labeled/selected/disabled/icon-only
semantics, remote activation and license-reader paging/Back focus restoration.
It captures synthetic button-state images for visual inspection.

## Manual remote acceptance

Use only Up/Down/Left/Right/OK/Back first. Repeat with media/channel keys when
available. Record app version, device/OS, stream container/codecs, sanitized
failure code and whether delay occurred in networking, parsing, or rendering.
Do not put provider URLs or credentials into screenshots, logcats or bug reports.

1. Clean launch → Welcome → M3U; navigate through every field without opening the
   keyboard. Press OK/Enter to edit; test Done and Back, preserved values and
   continued D-pad navigation. Repeat for Xtream, search, PINs and preferences.
   Test malformed URLs, unsupported schemes and missing Xtream credentials;
   confirm an inline error focuses the field without opening the keyboard.
   Test cancel during download and during large import.
2. Import an authorized live-only test playlist. Repeat with Xtream, including
   explicitly invalid auth/account responses. Restart; cached catalogue appears.
3. Browse All, a category and Uncategorized. Search including Unicode. Favorite
   a channel, watch it, return with Back; confirm the channel regains focus.
   Refresh with reordered/renamed channels and confirm surviving favorites.
4. Navigate lists using held D-pad input. Open/close all dialogs and the keyboard.
   Confirm focus visibility, no traps, expected Back ordering and root exit.
5. Verify missing/invalid logos and guide do not block watch. Import XMLTV with
   offsets spanning a DST transition; confirm current/next in device timezone.
6. Enable a PIN; lock a category and individual channel. Verify browser, search,
   Favorites, Recent, next/previous keys and direct repository entry fail closed.
   Try three wrong PINs, restart and confirm delay persists. Verify changes,
   disabling, editing the source and source deletion require authentication.
7. Clear history; clear cache (guide/logo only); delete source; verify encrypted
   source rows and associated tables removed. New setup still requires the PIN.
8. Repeat authorized imports, guide/logo fetching and playback over HTTP and
   HTTPS, including a custom provider port. Check HTTP-to-HTTPS same-host upgrades,
   refusal of HTTPS downgrades and cross-host redirects, and invalid certificates.
9. Repeat navigation with system animator duration scale at 0 and at the normal
   setting. Check focus and loading feedback on a lower-powered TV; verify source
   forms remain visible and scroll correctly with the vendor keyboard open,
   especially when editing the last field with a floating keyboard.
10. Inspect Font Awesome icons from normal TV viewing distance at 720p, 1080p and
    4K. Check normal, focused, selected, pressed and disabled contrast; large text,
    RTL back-arrow direction and wrapped settings/player controls. Verify play/pause
    is announced correctly by TalkBack and every icon-only target has a name.
    With networking unavailable, open Icon licenses and page with the remote.

## Playback qualification (physical TV required)

Use authorized controlled HTTP and HTTPS media, one provider connection at a time. The
placeholder playlist in `examples` is configurable; it is never contacted by
the application until the user provides a real source. Host your test playlist
over valid HTTPS first, then explicitly configured HTTP. TLS shortcuts are
deliberately unsupported.

Test SD, 720p and 1080p AVC/AAC; HLS MPEG-TS, HLS fMP4 and direct MPEG-TS separately.
Record actual codec profiles and decoder names externally in your test report.
Test alternate audio, embedded/sidecar HLS subtitles, Off and Fit/Fill. Check
unsupported tracks report an error without crashing. Qualify HEVC/MPEG-2/Dolby/4K
separately for each model before advertising them.

Exercise network disconnect/reconnect; 401/403/500; timeouts; invalid TLS;
long pause beyond the live window; Return to live; media keys; Home/standby/audio
focus loss/resume; channel changes during buffer/error; source deletion. Confirm
one active session and zero sessions outside foreground playback.

Measure these **targets**, not presently claimed results:

| Target | Procedure |
|---|---|
| Cached catalogue ≤3 s cold | 20 force-stop/relaunch samples with cached 10k catalogue |
| Focus typically <100 ms | High-frame-rate capture or frame trace on agreed device |
| 10k search ≤300 ms after debounce | Separate filter CPU time from 120 ms debounce |
| First picture/switch p95 ≤5 s | 100 controlled switches; separate fetch, app and decoder delays |
| Four-hour stability | Native/Java heap and sessions every 5 minutes; no sustained growth |
| 100 switches without leaks | Provider connection observation plus player/session counters in test harness |

An emulator can validate navigation/lifecycle but cannot qualify physical decoder,
remote, standby or long-playback compatibility. Record untested rows as untested.
