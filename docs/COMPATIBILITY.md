# Compatibility and deliberate limits

## Playback targets (require physical-device qualification)

HTTP/HTTPS HLS with MPEG-TS or fMP4 segments and direct HTTP/HTTPS MPEG-TS; baseline H.264
AVC video plus AAC audio, SD/720p/1080p. These are implemented Media3 targets,
not measured guarantees. HEVC, MPEG-2, Dolby audio, 4K and other tracks depend on
device decoders. Unsupported playback/decoder errors expose recovery actions.

No VOD/series UI, recording, catch-up, multiscreen, PiP, casting, external players,
custom DRM integration, VPN, cloud sync, QR/backend, marketplace, UDP or RTSP.

## M3U

UTF-8 Extended M3U, optional BOM, Unicode names, quoted/unquoted common attributes:
`tvg-id`, `tvg-name`, `tvg-logo`, `group-title`. Missing groups become Uncategorized;
missing display names fall back to tvg-name; entries with no name or unsafe URLs
are skipped. A valid header and at least one supported entry are required.
Relative URLs resolve against the final playlist response URL. Same-origin
redirects are allowed within the redirect limit.

Explicit `type=vod/movie/series` and common VOD file suffixes are omitted. Other
entries are treated as ambiguous live candidates, including EXTINF -1/0; duration
alone is unreliable. There is no claim to detect every VOD entry in mixed lists.
Per-entry custom HTTP headers, cookies and Kodi/VLC options are not executed.

Identity is SHA-256(source UUID + identity). A unique tvg-id is preferred and
survives signed-URL/title changes. Without one, the absolute URL is the fallback;
token changes can therefore change identity. Duplicate tvg-ids use tvg-id plus
URL, independent of playlist order. Adding/removing duplicate declarations or
changing their URLs can change fallback IDs; do not promise favorite preservation
in that ambiguous case. Exact duplicates are collapsed. Removed favorites are
pruned; favorites for surviving IDs persist. Categories are provider text.

## Xtream fixture-tested subset

`player_api.php?username=…&password=…` checks `user_info.auth`; explicit non-Active
status reports account unavailable. Optional `allowed_output_formats` is respected.
`get_live_categories` and `get_live_streams` support string/numeric IDs, absent
optional fields, unknown groups, duplicate IDs and explicit live-only stream types.

An explicit HTTP/HTTPS `direct_source` takes precedence. Otherwise the configured
server base path is retained and `/live/{encoded username}/{encoded password}/
{stream_id}.{extension}` is built. A supported per-channel `container_extension`
takes precedence over the user's HLS/TS choice. Providers requiring other paths,
headers or output formats need an adapter extension and test fixtures. No promise
is made that all Xtream-compatible providers use this convention.

Guide support: full `xmltv.php` (or an explicit XMLTV override), plus on-demand
`get_short_epg&stream_id=…&limit=20` for the playing channel. Short guide parsing
uses start_timestamp/stop_timestamp/end_timestamp Unix seconds and base64 titles
with a plaintext fallback. This subset is tested against synthetic responses;
no real provider/account behavior has been verified. Authentication failures are
not inferred from arbitrary 403 responses. No specific connection-limit response
has been qualified, so generic errors never falsely claim one.

## EPG

XMLTV matches exact stable tvg-id/epg_channel_id values. Display-name guessing is
intentionally omitted to avoid incorrect matches. Explicit timestamp offsets are
converted to UTC instants; an absent XMLTV offset means UTC. The UI formats time
in the TV's current timezone, including DST. Cache ingestion retains overlapping
programmes for today and tomorrow in the TV timezone. Missing/malformed/oversized
guides do not affect playback. Full failed refreshes retain the last good guide.
Current and next are refreshed in the UI every 30 seconds.

## Network and storage limits

- Explicit HTTP and HTTPS sources are supported; HTTPS remains preferred and
  uses normal system TLS validation. HTTP credentials and media are unencrypted.
  Android's declarative network configuration cannot list provider hostnames
  supplied at runtime, so a base cleartext opt-in is required. All application
  networking still passes the shared HTTP(S) URL and redirect checks. There is
  no WebView, arbitrary URI scheme, HTTPS fallback retry, or trust override.
- Same-origin redirects, maximum three, plus same-host HTTP-to-HTTPS upgrades
  (including a separate TLS port). HTTPS-to-HTTP downgrades, cross-host redirects,
  and same-scheme port changes are rejected. This conservative policy can reject
  redirect-based CDNs. Explicit HTTP(S) CDN stream/segment URLs work. Authorization is never
  forwarded to a redirect target.
- Metadata: 10 s connect, 20 s idle read, 45 s total; no transport retries.
- Catalogue: 16 MiB, 50,000 entries, M3U line length 16 KiB.
- XMLTV: 32 MiB, nesting 32, scanned programmes 500,000, retained 100,000.
- Short guide: 512 KiB, 100 parsed entries/channel. At most 100,000 cached rows.
- Logos: 1 MiB each, 15 s total, 96×96 decode request, 12 MiB memory cache.
- Compressed metadata responses are currently rejected; supply plain UTF-8 XMLTV.
  External XML entities/DTDs are rejected before expansion.
- Media: 10 s connect/20 s idle read, no total stream timeout, bounded retry.
  HLS manifests are capped at 2 MiB after decompression and a 45 s read budget
  (with up to one 20 s in-progress read before that budget is observed).

Changing the provider URL, username or source kind creates a fresh source UUID.
Password/display-name/guide changes retain it. Only one active source exists.

The cleartext configuration follows Android's documented
[network security configuration](https://developer.android.com/privacy-and-security/security-config).
A domain-only allowlist is suitable for fixed endpoints, rather than arbitrary
IPTV hosts entered by the user; request validation is the runtime boundary here.
