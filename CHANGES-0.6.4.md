# Krupas Media Probe 0.6.4 AUTO MP3 FIX — hardware test build

Base: verified 0.6.3 commit 6fe16f2. Separate branch fix/0.6.4-auto-mp3-fix; stable branches untouched.

User observations: com.qf.MusicPlayer reports PLAYING without title/artist/album while its UI displays title/artist. com.navimods.radio currently shows FM frequencies; an older saved Radio Złote Przeboje entry was historical. YouTube supplies fresh metadata but no title reaches the external display. No direct capture of the device UI tree or BLE status is available; these reports do not isolate a single hardware cause.

Changes: broader vendor title field selection with conservative fallback for small visible text panels; exclude playlists and controls; labelled radio PS/RDS fields no longer require known station names. Clear old accessibility RDS when the current screen no longer supplies it. A ten-second UI title grace is available only to the matching active session while Probe itself is foreground; YouTube cannot receive an old MP3 title. The diagnostics show current selected source, title, poll age, BLE permissions/connection, negotiated payload size and last GATT-confirmed text separately from historical snapshots. GATT success does not imply CAN display success.

BLE: UTF-8 title prefix fitting replaces silent rejection when title exceeds negotiated MTU. The full title remains available in Probe; the display may receive a shortened title at low MTU. Service discovery has a fallback when an MTU callback never arrives. UUIDs and ESP 1.9F plain-text heartbeat/clear protocol remain unchanged. No new ESP firmware is required by these changes.

CI: existing source ownership, lifecycle and ESP expiry tests plus UI filtering and UTF-8 payload tests; APK compilation and signature verification; unchanged ESP 1.9F compilation. Hardware acceptance is still required for MP3/FM/YouTube, low-MTU BLE and reconnect. Missing RDS is never fabricated from a frequency. Field dumps are local diagnostic samples from media apps, not uploads.

Full RDS: collect labelled PS/RT/RDS fields, including a clock inside an RDS field, with duplicate removal. Radio MediaSession title/display title/artist/subtitle/description are combined separately from music-title validation. Generic Android clocks and MP3 elapsed counters remain excluded. This forwards exposed RDS text, not raw tuner data that Android does not expose. Low MTU can shorten the combined text; full content is visible in current diagnostics.
