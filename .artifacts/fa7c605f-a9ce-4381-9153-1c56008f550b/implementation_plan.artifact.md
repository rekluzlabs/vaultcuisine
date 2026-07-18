# Implementation Plan - Welcome Screen with Interactive Image

The goal is to implement a new "Welcome" screen as the entry point of the app. This screen will feature the provided image as a full-screen background, with a clickable area over the "START YOUR CULINARY JOURNEY" button in the image.

## User Review Required

> [!IMPORTANT]
> You will need to save the provided image as `app/src/main/res/drawable/welcome_background.png` before the app can build and run successfully.

## Proposed Changes

### Navigation

#### [MODIFY] [NavRoutes.kt](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/ui/NavRoutes.kt)
- Add `Welcome` route.

#### [MODIFY] [MainActivity.kt](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/MainActivity.kt)
- Update `VaultCuisineNavHost` to start at `NavRoutes.Welcome`.
- Add the `WelcomeScreen` composable to the `NavHost`.

### UI Screens

#### [NEW] [WelcomeScreen.kt](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/ui/screens/WelcomeScreen.kt)
- Create a new Composable that displays the background image.
- Use `BoxWithConstraints` to overlay a transparent clickable area precisely over the button in the image.
- Navigation trigger to move to `NavRoutes.Home` when the area is clicked.

## Verification Plan

### Automated Tests
- I'll check for compilation errors after adding the new screen and updating navigation.

### Manual Verification
- Deploy to a device/emulator.
- Verify the `WelcomeScreen` appears first.
- Click the "START YOUR CULINARY JOURNEY" area to ensure it navigates to the Home screen.
- Verify the layout looks correct on different screen sizes.
