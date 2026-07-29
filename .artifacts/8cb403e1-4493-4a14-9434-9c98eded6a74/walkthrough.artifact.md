# Custom Brand Theming Walkthrough

The app's theming system has been overhauled to reflect your brand identity using Fresh Mint (#98D8C8) and Soft Cherry (#FFB7B2) as consistent accents across four distinct modes.

## Brand Accents
- **Fresh Mint**: Used as the primary color for buttons, icons, and highlights.
- **Soft Cherry**: Used as the secondary/accent color for contrasting pops of color.

## New Theme Modes

### 1. Pantry (Light)
A crisp, vintage kitchen look.
- **Background**: #F4F9F6 (Cool Mint Ice)
- **Surfaces**: #FFFFFF (Pure White)
- **Text**: #1E2925 (Dark Spruce)

### 2. Cellar (Dark)
A soft, dark forest shadow.
- **Background**: #161D1A (Deep Forest Shadow)
- **Surfaces**: #222C28 (Muted Spruce)
- **Text**: #E2ECE8 (Frosted Mint)

### 3. Deep Vault (AMOLED)
Pure battery-saving black.
- **Background**: #000000 (Pure Black)
- **Surfaces**: #121212 (Very Dark Gray)
- **Text**: #F0F4F2 (Clean White-Green)

### 4. Garden Fresh (Green)
A sage-themed immersive look where green is the star.
- **Background**: #D1ECE4 (Soft Sage Wash)
- **Surfaces**: #E6F5F0 (Light Cream-Mint)
- **Text**: #0F1A16 (Deepest Olive Black)

## Technical Implementation
- **Theme.kt**: Defined four separate `ColorScheme` objects and updated `VaultCuisineTheme` to dynamically switch based on the `themeMode` string.
- **Color.kt**: Centralized all brand and theme-specific color definitions.
- **Settings**: Updated the theme selection dropdown to use descriptive names for the new modes.
- **Persistence**: Updated `AppPreferences` to default to the "Pantry" theme and support the new theme IDs.

## Verification
- **Build**: Successfully compiled using `app:assembleDebug`.
- **UI Logic**: Verified that the theme updates in real-time when changed in settings.
