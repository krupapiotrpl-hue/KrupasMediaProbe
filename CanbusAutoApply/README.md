# TELE TECHNIKA – CANBUS Auto Apply (0.1 test)

Experimental APK for Roco K706, Zafira B, Android 13.
A separate project that **does not modify the existing Krupas Media Pro APK or ESP32 firmware**.

## What it does
* User enables the accessibility service in Android settings.
* Press **Naucz przycisku ZAPISZ**, navigates to vendor CANBUS settings and presses **Zapisz** manually once.
* Accessibility events capture package name and (when exposed) Save control's view ID.
* User can choose **TEST TERAZ** while parked.
* Optional automation attempts to open learned package and press **Zapisz** after Android **ACTION_SCREEN_ON**.

## Limits
* Does not connect to the CAN bus itself; only tries a UI re-apply.
* Android may block background activity launch.
* OEM CANBUS page may not be a launchable activity; a launcher intent may open the wrong page.
* OEM control may be drawn on a surface without accessibility nodes.
* May be disabled by the radio firmware's power management.
* Successful UI click is **not proof** CAN decoder was reinitialized.
* Only use with vehicle stationary.
* Does not request root, internet, or permission to change system settings.

## Build
In `CanbusAutoApply` run `gradle :app:assembleDebug` with JDK 17 and Android SDK 35.
GitHub Actions: `.github/workflows/canbus-auto-apply.yml`, APK artifact name `CANBUS-Auto-Apply-test`.

## First use
1. Install APK on the **radio**, not on the phone.
2. Android > Accessibility: enable `CANBUS Auto – sterowanie`.
3. Tap `Naucz przycisku ZAPISZ`, open OEM CANBUS settings manually, tap `Zapisz` once.
4. Return to app: package should be shown.
5. While parked, tap `TEST TERAZ`, verify the OEM menu opens and multimedia/coolant-temp return.
6. Only then enable automatic apply after wake.

If the learned package lacks a public activity, reliable full automation needs vendor-specific intent/API analysis.
