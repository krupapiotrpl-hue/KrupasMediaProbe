# KRUPAS Media Pro 1.3.12 — QF OEM Bluetooth probe

**Base:** 1.3.11 (`fix/kmp-1311-k706-overlay-call-20261117`)
**Target:** Roco K706 / QF001 / BLE Zafira Display / existing ESP firmware.
**Status:** experimental diagnostics, not a verified telephone-number reader.

## Changes
- Read-only probe for factory Bluetooth package `com.qf.bluetooth`.
- Observe passive vendor broadcast names/extra **key names only**.
- Best-effort read `sys.qf.bt.is.connect`; Bluetooth connection does **not** imply a call.
- Report findings on the KMP diagnostic screen under `QF OEM BLUETOOTH`.
- Existing HFP, UI, telephony, audio, media-routing and BLE call decisions are **unchanged**.
- No serial-port access, no GOC AT commands, no root, no MCU/CAN writes.

## Validation on K706
1. Install the APK as an update (same application ID and signing behavior as 1.3.11).
2. Ensure the car is stationary; launch KMP and tap **Kopiuj**.
3. With no call, record the `QF OEM BLUETOOTH`, `HFP BLUETOOTH`, and `DECYZJA T|` lines.
4. With an incoming call, record those lines (and note independently whether factory Bluetooth shows the number).
5. Repeat for an outgoing call and after hangup.
6. Test FM, MP3, DAB-Z and BLE continuity; report regressions before using a new call-reading implementation.
7. Share **only the diagnostic lines**, not a raw phone number or contact name.

## Decision rule
Only documented, observed, reliable OEM call-state events should activate `PHONE:`/T|. A music event, Bluetooth connection property, floating SystemUI window, or audio mode alone is not a verified phone call.

This stage determines **which OEM data is accessible**. It does not promise call-number display or work on VoIP without a tested source.
