# KRUPAS Media Pro 1.3.8 — test K706

- T| survives the temporary disappearance of the OEM Bluetooth phone window.
- Only an explicit HFP, telephony IDLE transition, or UI call-ended message clears a confirmed call.
- Brief positive accessibility signals persist long enough for the BLE loop to detect them.
- "DECYZJA T|" in the copyable diagnostic reports the call signal source, Bluetooth window, BLE and hold reason.
- Empty RDS separators and dangling hyphens are removed; MP3 title/artist separators are unaffected.
- Limitation: if K706 does not expose any hangup event, T| may remain active after the call. Copy diagnostics for further OEM investigation.
- Test cellular HFP, ChatGPT/Messenger over Bluetooth, hangup and FM/MP3/DAB.
