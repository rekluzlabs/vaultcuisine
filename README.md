# VaultCuisine

<p align="center">
  <img width="300" alt="VaultCuisine" src="icon4VC-removebg-preview.png" />
</p>

A privacy-first Android app that turns your recipe cards — printed or
handwritten — into clean, editable, structured recipes. On-device scanning
and recognition by default; cloud AI structuring is opt-in, uses your own
API key, and only ever sends a stripped, compressed copy of an image you
explicitly consent to sharing.

<!-- ![screenshot placeholder](docs/screenshot-1.png) -->

## Why VaultCuisine

Recipe boxes, cookbook margins, and grandma's handwriting shouldn't have to
live only on paper — but most "recipe scanner" apps quietly ship every photo
to a server. VaultCuisine doesn't do that by default, and never does it
without your explicit say-so.

## Features

- Scan any recipe, printed or handwritten, via camera or photo import
- On-device text recognition using ML Kit OCR — always runs locally, no
  network call, works on every device
- Optional AI structuring via your own Google Gemini API key (BYOK — Bring
  Your Own Key): reads the recognized text and/or image and returns a clean,
  structured recipe (title, ingredients, steps, timers). Gated behind an
  explicit first-use consent dialog before any image ever leaves the device
- Deterministic offline fallback: a regex-based heuristic structurer handles
  scans with no API key configured, or if you decline the consent prompt —
  scanning always works, structuring quality just varies
- Full editing control on every scanned recipe:
  - Edit any line of text inline
  - Add or remove ingredient/instruction lines
  - Drag and drop to reorder, including moving a line between Ingredients
    and Instructions
  - Long-press menu as a precision/accessibility alternative to dragging
- Re-scan a recipe from its original saved photo at any time — regenerates
  a fresh AI structuring pass without needing to re-photograph, and never
  overwrites existing data until you explicitly save
- "Doesn't look right?" manual retry on the review screen — resend the same
  photo for another pass, or retake it, without losing your place
- Serving-size scaling and metric/imperial unit conversion, both explicit
  opt-in per recipe — nothing is auto-converted
- Chef's notes and a 5-star rating per recipe

## How it works

```
Photo → ML Kit OCR (on-device, always) → structuring
                                              ├─ Gemini API key configured
                                              │  + consent given
                                              │  → cloud AI structuring
                                              │    (image + OCR text sent to
                                              │    Google's Gemini API)
                                              └─ no key / consent declined
                                                 → offline heuristic structuring
                                                   ↓
                                        Editable review screen (always)
                                                   ↓
                                         Saved to your local recipe vault
```

Scanned recipes are never auto-finalized. Every scan — regardless of which
path structured it — lands on an editable review screen before anything is
saved, since AI and heuristic structuring are both fallible, especially on
handwriting.

<h2 align="center">App Preview</h2>

<p align="center">
  <img src="images/vaultcuisine_preview.gif" width="300"/>
</p>

## Tech stack

- Kotlin + Jetpack Compose
- Room for local persistence
- ML Kit Text Recognition v2 for on-device OCR
- Google Gemini API (cloud, BYOK) for AI-assisted structuring of image and/or
  OCR text into a structured recipe
- `EncryptedSharedPreferences` for local storage of the user's own API key —
  never transmitted anywhere except directly to Google's API as
  authentication for that user's own requests
- Custom heuristic parser as a fully offline structuring fallback

## Requirements

- Android [X.X]+ (minSdk [XX])
- A Google Gemini API key (free tier available) if you want AI-assisted
  structuring — get one at [ai.google.dev](https://ai.google.dev). Not
  required to use the app; scanning and heuristic structuring work with no
  key at all.

## Project status

VaultCuisine is in early alpha testing, so initial releases are strictly
tech demos.

**Known limitations:**

- Handwriting recognition accuracy varies by card legibility. Actively being
  tuned — recent work includes image preprocessing improvements, structured-
  output constraints on the AI response, and both automatic and manual retry
  paths for scans that come back low-confidence or visibly wrong.
- Printed recipe structuring is solid; occasional ingredient/instruction
  boundary errors are still being tuned.
- No real Room migrations yet — schema changes currently wipe local data
  (`fallbackToDestructiveMigration`). This is acceptable only during solo
  dev testing and will be fixed before any real release.

**Roadmap:**

- [ ] Real Room migrations (replace destructive fallback) before public
      release
- [ ] Live end-to-end testing of the Gemini upload path across a broad set
      of real handwritten cards
- [ ] Import/Export/print formatting
- [ ] Confidence-level highlighting surfaced in the review screen UI (data
      model already supports it)
- [ ] Meal planner, pantry inventory, cooking mode

## Privacy

VaultCuisine does not collect analytics, and there are no ads, no IAP, and
no subscriptions.

- On-device OCR (ML Kit) always runs locally — no network call, no data
  leaves your device for this step, regardless of settings.
- Cloud AI structuring is fully opt-in: it only runs if you've entered your
  own Gemini API key **and** accepted an explicit first-use consent dialog.
  Declining, or having no key configured, falls back to fully offline
  heuristic structuring — the app never silently uploads anything.
- When cloud AI structuring is used, a stripped (EXIF removed) and
  compressed copy of the relevant image is sent directly to Google's Gemini
  API using your own key. VaultCuisine's own servers never see it — there
  are no VaultCuisine servers. The original full-quality photo stays on your
  device only.
- Your API key is stored locally via `EncryptedSharedPreferences` and is
  never transmitted anywhere except as authentication on your own requests
  to Google's API.

See [PRIVACY.md](PRIVACY.md) for full details.

## Contributing

This is currently a solo-developed project. Issues and suggestions are
welcome; beta testing dates TBD.

## License

<p>All Rights Reserved. See <a href="https://github.com/rekluzlabs/vaultcuisine/blob/main/LICENSE.md">LICENSE.md</a> for details.</p>
