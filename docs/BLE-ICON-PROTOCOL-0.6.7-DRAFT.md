# KRUPAS 0.6.7 AUTO — proposed BLE icon protocol (NOT ENABLED)

Current ESP 1.9f BLE RX accepts raw UTF-8 titles. Do not send control frames to deployed firmware.

## Proposed v1 framed message for a separate, opt-in ESP test build
Prefix: `@KP1|`; fields separated by ASCII `|`, UTF-8 text percent-encoded so separator/newlines are safe.

`@KP1|SRC|RADIO|<encoded-title>`
`@KP1|SRC|YOUTUBE|<encoded-title>`
`@KP1|SRC|MP3|<encoded-title>`
`@KP1|SRC|NAV|<encoded-maneuver>`
`@KP1|CALL|ACTIVE|<encoded-contact-or-number>`
`@KP1|CALL|END|`

Rules:
- Enabled only after both APK and ESP advertise verified protocol support; until then transmit legacy titles only.
- Icons are drawn by ESP using verified Opel display glyph/CAN mapping, never by sending Unicode emojis as text.
- Call display has highest priority; fallback is phone icon with blank contact when caller identity cannot be read.
- Navigate only while a real maneuver exists; no static NAV label.
- Preserve temperature/status safety messages and five-second media rotation.
- Never reinterpret arbitrary legacy media title as a control frame.
- Version existing 0.6.6 APK and currently flashed ESP separately; never overwrite.

## Verification required
1. Confirm handshake/feature negotiation and frame limits against ESP BLE MTU.
2. Test phone name/number availability in K706 notification or OEM BT UI.
3. Verify glyph mapping, CAN priority and text layout in car before enabling.
4. Build and install diagnostic 0.6.7 separately; verify installer signature and package/version.
