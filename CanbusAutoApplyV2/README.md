# CANBUS Auto Apply 0.2 – screen-gesture learning (experimental)

0.1 needed the OEM UI to expose a 'Save/Zapisz' AccessibilityNode / click event. The Roco K706 did not expose this: screenshot confirmed **service active, learning in progress, saved app: none**.

This version uses an Accessibility Service's TYPE_ACCESSIBILITY_OVERLAY and dispatchGesture to record and replay up to 12 screen touch coordinates. It **does not** need to find or read button labels.

## Install
This build is a **different applicationId** from 0.1 because independently built debug APKs are signed with different keys. Disable/uninstall the older `CANBUS Auto Apply 0.1` so two accessibility services are not active simultaneously.

Install 0.2 **on the radio**; enable "CANBUS Auto 0.2 – dotknięcia" under Accessibility settings.

## Record
1. Park vehicle, turn ignition on.
2. Open 0.2 and tap "NAGRAJ NOWĄ SEKWENCJĘ". Android goes HOME.
3. Floating bar "DODAJ PUNKT"/"GOTOWE" appears at top right.
4. Tap "DODAJ PUNKT". A semi-transparent full-screen capture appears. Tap the icon/menu target you would normally tap to reach CANBUS. The app stores and replays that touch once; wait for the screen transition.
5. Repeat (DODAJ PUNKT + target) for every menu step from HOME to the final **Zapisz**.
6. Press "GOTOWE". Open 0.2, press "TEST ZAPISANEJ SEKWENCJI". Check whether Multimedia and coolant temperature return.
7. Only after a successful test enable "Automatycznie 8 s po wybudzeniu".

## Limitations
- TEST controls the actual screen; do not drive or operate the vehicle during test.
- The script expects the **same screen size, orientation, home layout, and menu organization**. A popup, dialog, or menu change may send touches to the wrong place.
- Playback only proves touches were delivered, not that CANBUS was initialized.
- The radio may not emit ACTION_SCREEN_ON when ACC wakes it; if so the automatic trigger needs vendor-specific integration.
- CANBUS settings may require an OEM password. You must account for it manually in macro, or better avoid password entry by launching a public OEM activity, if one exists.
- Neither serial CAN protocol nor Simple/OP-SS-01 configuration is modified.
- No network, root, or personal-data access requested.
- Auto is disabled by default.

Build: `gradle :app:assembleDebug` with JDK 17, Gradle 8.10.2, Android SDK 35. GitHub Actions artifact: `CANBUS-Auto-Gesture-0.2-test`.
