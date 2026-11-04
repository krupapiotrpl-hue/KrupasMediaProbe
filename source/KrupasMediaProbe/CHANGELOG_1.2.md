# 1.2-navradio

- Read playing radio MediaSessions instead of skipping radio packages.
- Read currently active ongoing radio notifications as fallback, including while the radio UI is hidden.
- Explicit paused/stopped radio sessions suppress notification fallback.
- Filter standalone TODO with invisible whitespace and optional source prefixes, including at the BLE send boundary. Preserve legitimate TOTO artist names.
- Compact horizontal buttons, copy diagnostics, exact version in diagnostics.
- Match APK filename and internal version (versionCode 120).

Validation: unit tests for metadata filtering and radio identification run before APK build. Device integration, NavRadio firmware metadata availability, ESP command compatibility, and actual absence of TODO require hardware confirmation. Existing 1.9x17 ESP firmware is not changed.

Signing: CI uses an ephemeral Android debug keystore; same-signature in-place upgrades are not guaranteed. Do not advise uninstalling an existing app without explaining this limitation.
