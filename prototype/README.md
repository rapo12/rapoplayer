# RAPOPLAYER HTML prototype

Open **index.html** in a modern browser. It is one self-contained file: no build,
installation, server, account, or internet connection is required.

The prototype includes Live, category browsing, search, favorites, recent channels,
welcome/source setup, simulated playback and track selection, settings, and a
household PIN demonstration. Use a mouse, Tab, arrow keys, Enter, or Escape.
Press `/` to search. Open `index.html?welcome=1` to start at the welcome screen.

All channel names, programmes, track choices, and vector artwork are fictional.
No channel or stream is provided. **Playback and imports are simulations.**
The source form makes no network requests; connection details and PINs are never
saved. Use placeholders instead of real credentials. The PIN demonstration uses
**1234** and provides no actual security protection.

Only fictional channel IDs for favorites/history and playback preferences are
saved in localStorage. If browser storage is unavailable, the page still works
for the current session. Settings → Reset demo data restores the initial demo.

This is a visual prototype for the native Android TV app in the parent directory.
It does not change that application's source, settings, or data.

Verified in headless Chrome at 1440×1000 and 390×844: **37 browser checks passed**,
including keyboard navigation, focus restoration, favorites/history, search,
mock import cancellation, track controls, the PIN flow, local-storage privacy,
and mobile overflow checks. No JavaScript errors or external page requests were
detected. The generated `verification.json` stays local and is excluded from Git.

Visual previews: [Desktop](previews/desktop.png) · [Mobile](previews/mobile.png).
The preview clock uses Africa/Casablanca time.
