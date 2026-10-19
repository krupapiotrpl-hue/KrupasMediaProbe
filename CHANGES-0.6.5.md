# 0.6.5 hardware test build

Base: verified 0.6.4 commit b7e7bd0. User confirms external MP3 title (with jitter) and YouTube title now work. FM source com.android.fmradio.ext has no current metadata; a literal none was previously GATT-confirmed. Accessibility sample includes preset labels, including a stored RMF preset. A preset is not evidence of the tuned station or live RDS.

Reject none/null/undefined/unknown/N/A sentinels from sessions, notifications and UI through shared title/RDS filters. Debounce changing MP3 UI text for 600 ms and tolerate missing redraw fields for at most 2500 ms; reset on source changes. Radio UI selection uses only explicit live RDS/PS/RT fields and excludes preset IDs; remove broad station-name scanning that could mistake a stored preset for the tuned station. Full exposed RDS including radio-clock text is retained. BLE protocol and firmware remain as in 0.6.4.

Tests cover placeholders, preset rejection, MP3 title/artist redraw oscillation, real track changes, missing-title expiry and source reset. Existing tests, APK signature verification and unchanged firmware compilation retained. Live FM remains unverified: a full field sample showing actual PS/RT (not a preset) may still be required. No RDS is fabricated from the frequency or preset list.
