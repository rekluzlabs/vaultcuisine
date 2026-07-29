# Custom Brand Theming Implementation Plan

Implement four distinct themes (Pantry, Cellar, Deep Vault, Garden Fresh) using the brand identity colors (Fresh Mint and Soft Cherry) as accents.

## Proposed Changes

### UI Theme & Colors

#### [MODIFY] [Color.kt](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/ui/theme/Color.kt)
- Define brand accent colors: `FreshMint` (#98D8C8) and `SoftCherry` (#FFB7B2).
- Define background, surface, and text colors for all four themes:
  - **Pantry (Light)**: Background #F4F9F6, Surface #FFFFFF, Text #1E2925.
  - **Cellar (Dark)**: Background #161D1A, Surface #222C28, Text #E2ECE8.
  - **Deep Vault (AMOLED)**: Background #000000, Surface #121212, Text #F0F4F2.
  - **Garden Fresh (Green)**: Background #D1ECE4, Surface #E6F5F0, Text #0F1A16.

#### [MODIFY] [Theme.kt](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/ui/theme/Theme.kt)
- Define four separate `ColorScheme` objects: `PantryColors`, `CellarColors`, `DeepVaultColors`, and `GardenFreshColors`.
- Update `VaultCuisineTheme` to accept a `themeMode: String` and apply the corresponding color scheme.
- Use `FreshMint` as `primary` and `SoftCherry` as `secondary`/`error` accents.

### Data & Logic

#### [MODIFY] [AppSettings.kt](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/data/AppPreferences.kt)
- Update default theme to "pantry" (or keep "cellar" if preferred).
- The theme names will be: `pantry`, `cellar`, `vault`, `garden`.

#### [MODIFY] [MainActivity.kt](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/MainActivity.kt)
- Simplify theme selection logic to pass `settings.theme` directly to `VaultCuisineTheme`.

### Settings UI

#### [MODIFY] [SettingsScreen.kt](file:///C:/Android_Projects/VaultCuisine/app/src/main/java/com/rekluzlabs/vaultcuisine/ui/screens/SettingsScreen.kt)
- Update the Theme dropdown options to:
  - "pantry" -> "Pantry (Light)"
  - "cellar" -> "Cellar (Dark)"
  - "vault" -> "Deep Vault (AMOLED)"
  - "garden" -> "Garden Fresh (Green)"

## Verification Plan

### Automated Tests
- Build success check.

### Manual Verification
- Switch between all four themes in Settings.
- Verify background, surface, text, and accent colors match the specifications for each mode.
- Ensure transitions are smooth and the UI remains legible in all modes.
