# VaultCuisine — Project Context

Last updated: 2026-07-18

## Architecture decisions (do not relitigate without discussion)

- AICore/Gemini Nano fully removed (commit 89efe31). App is online-only for
  AI structuring now. Do not reintroduce on-device inference.
- Recipe capture flow: ML Kit Document Scanner (crop) -> original saved
  full-quality to recipe_images/{recipeId}.jpg (app-private) -> stripped/
  compressed copy sent to Gemini -> stripped copy discarded after each call.
  Original is the ONLY persisted image; Gemini's copy is always transient,
  regenerated fresh each time (including on re-send).
- RecipeStructurer interface (text-based) is unchanged; ImageCapableStructurer
  added alongside it. GeminiOcrClient implements both. HeuristicStructurer
  remains the fully-offline fallback and must never be removed — it's what
  keeps the app usable with no API key / no network.
- 3-tier fallback in MainViewModel.processScannedImage: Gemini image mode ->
  Gemini text mode (rare, only when image bytes unavailable) -> Heuristic
  (always succeeds, never dead-ends the user).
- Confidence field (String? = null) added to Recipe/RecipeIngredient/
  RecipeStep — optional, must not break existing constructor calls.
- EXIF stripping happens via decode/recompress through Bitmap (metadata is
  dropped automatically since Bitmap doesn't carry EXIF) — order between
  strip/compress doesn't functionally matter but spec says strip runs last.
  Verified via ImagePreprocessorExifTest.kt (injected Make/Model/DateTime/
  GPS tags, asserted null on output).
- Re-send flow: implemented. Re-scan button on recipe detail/edit screen,
  reads from Recipe.sourceImagePath, regenerates a fresh stripped copy via
  ImagePreprocessor each time (never cached), runs the same 3-tier fallback
  as first-time scan. Does NOT auto-overwrite existing recipe data — lands
  on the review/edit screen with new results pre-filled, old data intact
  until explicit save. Button hidden if sourceImagePath is null.
- Consent flow: implemented. First-use dialog before any image upload to
  Gemini, gated via a single shared awaitGeminiConsent() helper used by
  BOTH processScannedImage and rescanRecipe — any future Gemini call site
  should use this same helper rather than reimplementing the check.
  Rejecting consent falls back silently to HeuristicStructurer, no dead-end.
  Consent resets to unaccepted whenever the API key is changed/re-saved.
- Scaling: implemented. Recipe.servings: Int? (default null, opt-in).
  util/AmountParser.kt parses free-form amount strings (integers, decimals,
  simple/mixed fractions, ranges including en-dash) into numeric values for
  display-time scaling only — never mutates stored data. Unparseable
  amounts ("a pinch", etc.) display unscaled with a "~" marker. Scaling
  control on RecipeDetailScreen, hidden entirely if servings is null.
- Unit conversion: implemented. Recipe.preferredUnitSystem field
  (AS_WRITTEN / METRIC / IMPERIAL, default AS_WRITTEN), per-recipe. Built on
  AmountParser's numeric output — util/UnitConverter.kt classifies units into
  VOLUME (cup/tbsp/tsp/ml/l/fl_oz), WEIGHT (g/kg/oz/lb), and COUNT/never-
  converted (cloves/pinch/cans/pieces/null/unknown). Conversion factors cited
  to NIST SP 811, commented in code. Rounding: ml <100 nearest 5, ml >=100
  nearest 10, L to 1 decimal, g nearest 5, kg to 1 decimal. Imperial: metric
  weight conversion uses oz below ~2 lb (nearest 0.5 oz) and lb at/above
  that threshold (nearest 1/4 lb) — avoids coarse half-pound rounding on
  small quantities; locked in by 4 threshold tests in UnitConverterTest.kt
  (300g, 500g, 1kg, 2kg). Volume uses nearest 1/4 cup / 1/2 tbsp / 1/4 tsp.
  Composes with scaling correctly: scale first via AmountParser, then
  convert the scaled value (avoids compounded rounding). Toggle on
  RecipeDetailScreen below the scaling stepper.
- Chef's notes / rating: implemented. Recipe.notes (existing field) reused
  for user-facing notes rather than adding a separate chefNotes field — it
  was already user-visible/searchable/printable and populated by Gemini
  import, so a second field would've been redundant. Recipe.rating: Int?
  (nullable, default null, range 1-5) added as a new field. Star rating is a
  quick-save (tap sets/toggles immediately via setRecipeRating(), same
  read-copy-upsert pattern as setRecipeUnitSystem — no edit-mode save
  needed). Notes are edited via the normal structured edit form/saveEdits().
- Notes/rescan interaction: notes = recipe.notes explicitly preserved in
  BOTH rescanRecipe code paths (Heuristic and Gemini) — user's notes survive
  a re-scan even when every other field gets replaced by new scan results.
  rating has no interaction with re-scan at all.
- Fallback-message/notes collision fix: HeuristicStructurer.absoluteFallback()
  writes a canonical FALLBACK_NOTES_MESSAGE constant (defined once in
  Recipe.kt, referenced from HeuristicStructurer — single source, no
  duplicate string) into notes when a scan can't be parsed. RecipeDetailScreen
  checks for an exact match against this constant and shows a dedicated
  banner instead of displaying it as if it were user-authored content;
  entering edit mode on such a recipe pre-clears the field to
  empty/placeholder rather than letting the user accidentally keep/append to
  the system message.
- AppDatabase version history: v2->v3 (confidence fields), v3->v4 (servings
  Int -> Int?), v4->v5 (preferredUnitSystem), v5->v6 (rating).
  fallbackToDestructiveMigration currently papers over ALL FOUR bumps with
  full data loss on every version change — see TODO below.

## Known TODOs / deferred work

- Replace fallbackToDestructiveMigration in AppDatabase.kt with a real
  Migration (or Room auto-migration) before any real release — currently
  wipes all user data on schema change, acceptable only during solo dev
  testing. Now spans FOUR schema bumps (v3-v6) that each need explicit
  migration handling, not just one:
    - v3 (confidence fields): straightforward nullable-column additions.
    - v4 (servings Int -> Int?): SQLite's ALTER TABLE cannot change a
      column's nullability/type in place. A naive ALTER TABLE ADD COLUMN
      will FAIL since the column already exists. Correct approach: add a
      new nullable column under a different name, explicitly UPDATE it to
      NULL for all existing rows (discarding old default value `4` on
      purpose), then drop the old column and rename — or use a
      CREATE TABLE ... AS SELECT + DROP + RENAME rebuild. Skipping the
      explicit NULL step silently leaves every old recipe with
      servings = 4, defeating the point of making it opt-in.
    - v5 (preferredUnitSystem): straightforward nullable-column addition,
      default AS_WRITTEN.
    - v6 (rating): straightforward nullable-column addition, default null.
  When this is finally tackled, write one consolidated migration path
  (or a chain of Migration objects) covering all four steps together —
  don't assume only the servings case needs care.
- Live end-to-end test of the Gemini upload path (real API key, real recipe
  card scan, confirm actual response quality on review screen) has NOT been
  run yet as of this note — everything is verified at code/unit-test level
  only.
- Voice wake-word timer feature — intentionally deferred, fully decoupled
  subsystem, do not entangle with recipe data model.
- AI-assisted OCR structuring UI polish (confidence highlighting in review
  screen) — data model supports it, UI doesn't use it yet.
- Meal planner, pantry inventory, cooking mode — not started (Phase 3).

Phase 2 (scaling, unit conversion, chef's notes/rating) — COMPLETE.

## Non-goals

- No multi-provider AI abstraction — Gemini only, deliberately, no LiteLLM.
- No sending the original uncompressed image to Gemini — always strip+compress
  first.
- No auto-save on AI structuring — user must review and explicitly save,
  both for first-time scans and re-sends.
- No auto-converting units on existing recipes — AS_WRITTEN is always the
  default, conversion is explicit per-recipe opt-in only.