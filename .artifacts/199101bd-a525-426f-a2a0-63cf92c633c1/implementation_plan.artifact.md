# Implementation Plan - Change Background Color to Black

The user wants to change the background color of the app icon (and likely the splash screen) from white to black. Based on the provided screenshot and the active file, this involves modifying the adaptive icon's background drawable and updating the app theme to ensure the splash screen matches.

## Proposed Changes

### [Icon Background]

#### [MODIFY] [ic_launcher_background.xml](file:///C:/Android_Projects/VaultCuisine/app/src/main/res/drawable/ic_launcher_background.xml)
- Change the `android:fillColor` of the primary background path from `#3DDC84` (green) to `#000000` (black).
- Note: The user mentioned it is currently "white", but the file on disk shows the default Android green. Changing it to `#000000` will ensure it is black regardless of its current state.

### [App Theme / Splash Screen]

#### [MODIFY] [themes.xml](file:///C:/Android_Projects/VaultCuisine/app/src/main/res/values/themes.xml)
- Add `android:windowSplashScreenBackground` set to `@color/black` to ensure the entire splash screen background is black, matching the new icon background.

## Verification Plan

### Automated Tests
- N/A (Visual change)

### Manual Verification
- Deploy the app to a device/emulator.
- Observe the app icon on the home screen to verify the black background.
- Launch the app and observe the splash screen to verify the background is black.
