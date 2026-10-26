# Architecture

One app module with proportionate package boundaries:

```
MainActivity → Compose UI → MainViewModel → Repository → Room / Crypto
                                   ↓            ↓
                          PlaybackController   Network
                                   ↓            ↓
                            Media3 / OkHttp    source adapters / parsers
```

`core` contains normalized models, stable identity, pure browsing, URL rules,
M3U/Xtream/XMLTV parsers and failure/retry policy. `data` owns bounded transport,
Room transactions, source configuration encryption, preferences and PIN gates.
`player` owns the sole ExoPlayer and MediaSession. `ui` observes immutable state
and sends actions to the ViewModel. There is no service, backend, dependency
injection framework, cross-platform wrapper, or speculative module split.

Parsing runs on OkHttp workers or Dispatchers.IO. Searching runs on Default
after a 120 ms debounce. LazyColumns virtualize channels and categories. Only
visible logos are decrypted and loaded; decoded images have a 12 MiB memory
cache, 96×96 requests and no disk cache. Catalogue lists are bounded at 50,000
items. History is bounded to 200 channels; guide storage to 100,000 programmes.
These limits bound memory but are not a substitute for real-device profiling.

Refresh downloads and validates the new catalogue before opening a Room
transaction. The transaction swaps channel metadata and encrypted URLs together,
retains favorites with surviving IDs, and commits the encrypted configuration
last. Failure/cancellation retains the old catalogue and credentials. Mutating
imports and full-guide refreshes are serialized. Obsolete playback/guide requests
are canceled; source identity is checked before guide commits and player start.

Room schema 1 is exported to `app/schemas`. There is no previous schema to migrate.
Future changes must add explicit migrations and migration tests; destructive
migration fallback is intentionally absent. Room uses a truncating rollback
journal with SQLite secure deletion enabled in the application.

## Secrets

The source configuration (including source/display URLs, username/password and
optional EPG URL) and each channel's stream/logo URLs are AES-256-GCM encrypted
in private Room columns. The non-exportable Android Keystore key manages the
cipher. Every encryption uses a fresh IV and authenticates the source/channel ID
as associated data. Channel names/categories/guide titles and stable hash IDs
are separate metadata. Favorites and history contain only source/channel IDs.

The PIN uses PBKDF2-HMAC-SHA256 with a random 128-bit salt, 210,000 iterations,
a 256-bit verifier and constant-time verification. Neither the PIN nor source
secrets enter DataStore or saved-instance state. Administrative grants expire
after two minutes and are revoked on foreground transitions. Individual playback
grants apply to the verified channel only. Repository playback URL access checks
both channel and category restrictions, irrespective of the UI destination.

Backups/device transfer exclude all app data and `allowBackup` is false. Deleting
a source removes credentials and associated metadata atomically while retaining
the household PIN. Keystore invalidation fails closed with an actionable reset
message. It does not silently remove restrictions or replace a key and pretend
decryption succeeded.

HTTP is allowed only through explicit HTTP(S) URLs. A base Android cleartext
opt-in accommodates unknown provider domains; the shared transport rejects unsafe
schemes and redirect destinations, including all HTTPS-to-HTTP redirects. HTTPS
continues to trust only normal system CAs. No URL or credential is added to errors.

There are no analytics, crash-upload integrations, logging interceptors, HTTP
caches, cookies, or authenticator callbacks. Media3 logging is disabled because
library exceptions may contain signed URLs. Diagnostics use a small allowlist
(version, count, failure enum), never exception messages or source hostnames.

## Player lifetime

The ViewModel owns one controller. A channel change stops and clears the old
media item before preparing another. An epoch cancels pending retry callbacks;
canceling an obsolete source lookup prevents stale playback. Audio focus and
becoming-noisy handling use Media3. OnStop and final disposal release player and
session. Standby therefore creates no background provider connection. Resume
requires explicit action, at the current stream's live position.

There is no persistent buffer or promised rewind. Live HLS can pause within the
provider's available window; behind-live-window errors recover once to default
position and explain the recovery. Non-seekable live resume reconnects at the
edge. Network retries use 1 s then 2 s backoff, at most twice; auth/403/decoder
errors are not automatically retried.

## Remote input and motion

All inputs use the shared state-based Compose Input component, including Search.
The previous value-based Compose overload ignores showKeyboardOnFocus. The new
component keeps it false during navigation, enables the input connection only
after explicit OK/Enter or pointer input,
and treats directional keys as focus navigation outside editing. IME Done and
Back keep entered text and restore navigation on the same field. Modern IME Back
handling is also observed through keyboard insets. Password/PIN fields use
OutlinedSecureTextField with explicit Hidden/Visible modes. TextFieldState uses
ordinary remember, never a saved-state saver, so connection secrets stay in RAM.
Navigation uses the field's bounds rather than Compose's caret-based focus
rectangle, so empty inputs are not skipped. Dialogs receive edge-to-edge insets
and apply keyboard padding outside the bounded surface, leaving the form's
scroll viewport above the keyboard.
For a floating TV keyboard that reports visibility with a zero-height inset,
the dialog uses a compact editor in the upper half of the screen. Normal IMEs
continue to use their reported insets; hiding the keyboard restores the centered
dialog. Vendor keyboards still require device qualification.
An active editor requests its full bounds into view after IME visibility or the
dialog window size changes, so a focused lower field follows the resized scroll
viewport. This uses Compose's existing relocation API after a layout frame.

The source form has a bounded, scrollable column so invalid fields can receive
focus even when outside the viewport. Catalogue lists remain virtualized. PIN
verification belongs to the dialog coroutine and is canceled when it closes;
the waiting state no longer shows a premature incorrect-PIN message.

Screen/dialog entrances use one graphics layer and a 150 ms, 6 dp fade/translation.
TV buttons and Material input focus transitions use their existing lightweight
Compose animations. They use the normal system motion-duration scale, including
zero; no outgoing player/screen is kept alive for a crossfade. Loading text changes
share the short entrance timing, while Material progress indicators remain bounded
in size. No new dependencies were introduced.
