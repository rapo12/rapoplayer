# Publishing readiness (reviewed 2026-10-25)

No app store submission, signing-secret upload or service purchase has been
performed. The debug build is for installation and qualification. Source branch
publishing is separate from an app store release.

- [x] TV-only Leanback launcher, Internet permission, no touchscreen requirement,
  landscape activity and original icon/name/banner assets.
- [x] 320×180 xhdpi English banner with the app name; scalable square vector icon.
- [x] English text resources, start/RTL-aware layout primitives, remote controls.
- [x] API 28 minimum; compile/target 36; stable centrally pinned dependencies.
- [x] Normal TLS and safe redirects; explicit HTTP compatibility; no bundled media/credentials or analytics.
- [ ] Verify all [TV app quality criteria](https://developer.android.com/docs/quality-guidelines/tv-app-quality),
  including actual launcher icon size (at least 160×160 xhdpi), D-pad focus,
  Back behavior, overscan, accessibility and remote-only setup on each device.
- [ ] Recheck [current target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
  immediately before submission. As of the review, the documented Android TV
  submission exception requires API 34 or above; this project targets 36.
- [ ] Run complete emulator and physical-device acceptance; record supported
  formats per model, stability, performance, network loss and track switching.
- [ ] Inspect the final release APK/AAB for transitive `.so` files, 64-bit ABIs and
  [16 KiB page-size support](https://developer.android.com/guide/practices/page-sizes).
  Java/Kotlin source alone does not prove transitive native-library compliance.
  Recheck the applicable Play deadline and test the actual artifact in a 16 KiB
  environment; don't rely on an assumption about library packaging.
- [ ] Create and safely retain a release signing key outside this repository;
  configure Play App Signing and private CI secrets without committing them.
- [ ] Build and install a minified release; inspect exported components, backup
  rules, permissions, network behavior and dependency/license notices.
- [ ] Finalize application ID/versioning, store listing, TV screenshots, content
  rating, original store graphics and localized launcher assets where needed.
- [ ] Publish a privacy policy explaining local credentials, source metadata,
  direct provider/CDN traffic, deletion, backups, household PIN/reset limitations,
  absence of analytics, and the user's responsibility to use authorized sources.
- [ ] Complete Play Data Safety based on the shipped artifact and provider/network
  behavior. Review applicable content and user-provided-content policies.
- [ ] Establish signing/build provenance, dependency update process, supported
  device matrix and a support path that accepts only sanitized diagnostics.

Tooling choice: AGP 8.13.2 + Gradle 8.13 + JDK 17 is a documented compatible stable
combination for SDK 36. Compose BOM 2026.03.00 is pinned because newer BOMs can
require SDK 37/AGP 9.1. Current stable is not automatically a compatible upgrade.
See [AGP compatibility](https://developer.android.com/build/releases/agp-8-13-0-release-notes),
[Compose for TV](https://developer.android.com/jetpack/androidx/releases/tv),
[Media3](https://developer.android.com/jetpack/androidx/releases/media3) and
[player lifecycle guidance](https://developer.android.com/media/media3/exoplayer/hello-world).

The functional reference website was read as a description of the category only.
No reference-app binary, behavior, proprietary source or assets were tested or copied.
