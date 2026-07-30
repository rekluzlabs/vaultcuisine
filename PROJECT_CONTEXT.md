# VaultCuisine — Project Context

Last updated: 2026-07-30

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
  NOTE (2026-07-30): ImagePreprocessor.prepareForUpload was rewritten to a
  single decode -> resize -> enhance -> encode pass (previously a two-pass
  encode at quality 80 then re-encode at 95). EXIF is still dropped as a
  side effect of the Bitmap round-trip, same as before — re-verify
  ImagePreprocessorExifTest.kt still passes against the new method shape,
  it was not re-run as part of this session.
- Re-send flow: implemented. Re-scan button on recipe detail/edit screen,
  reads from Recipe.sourceImagePath, regenerates a fresh stripped copy via
  ImagePreprocessor each time (never cached), runs the same 3-tier fallback
  as first-time scan. Does NOT auto-overwrite existing recipe data — lands
  on the review/edit screen with new results pre-filled, old data intact
  until explicit save. Button hidden if sourceImagePath is null.
  NOTE (2026-07-30): a second, distinct rescan entry point now also exists
  directly on the review screen itself ("Doesn't look right?" — see below).
  The two are not the same flow: the detail-screen re-scan re-reads from
  sourceImagePath after a recipe is already saved; the review-screen manual
  retry operates on in-memory bytes from the scan currently being reviewed,
  before anything is saved. Keep both — they cover different points in the
  lifecycle.
- Consent flow: implemented. First-use dialog before any image upload to
  Gemini, gated via a single shared awaitGeminiConsent() helper used by
  BOTH processScannedImage and rescanRecipe — any future Gemini call site
  should use this same helper rather than reimplementing the check.
  Rejecting consent falls back silently to HeuristicStructurer, no dead-end.
  Consent resets to unaccepted whenever the API key is changed/re-saved.
  TODO: confirm the new manual "Try again" path (2026-07-30) and the
  confidence-triggered auto-retry both route through awaitGeminiConsent()
  correctly, or whether they're exempt as continuations of an
  already-consented scan — not explicitly verified this session.
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
- Gemini OCR accuracy pass (2026-07-30, this session):
  - GeminiOcrClient now sends `generationConfig` with `temperature: 0.1`,
    `topP: 0.95`, `maxOutputTokens: 2048`, `response_mime_type:
    "application/json"`, and a full `response_schema` matching
    GeminiRecipeDto. Removes reliance on prompt-only "return only JSON"
    instructions and the markdown-fence-stripping parse step is now a
    defensive no-op rather than load-bearing.
  - Schema JSON is built with explicit `JsonPrimitive(...)` wrapping rather
    than the kotlinx.serialization.json convenience `put(String, String/
    Number/Boolean)` extension overloads — the latter caused
    "Argument type mismatch: actual type is String, but JsonElement was
    expected" compile errors against this project's pinned
    kotlinx-serialization-json version. If touching this file again, keep
    using explicit JsonPrimitive() for scalar values inside buildJsonObject/
    buildJsonArray rather than reintroducing the convenience overloads.
  - ImagePreprocessor.prepareForUpload rewritten: single decode -> resize ->
    grayscale/contrast enhance (contrast 1.6f, brightness -60f — deliberately
    lighter than TextRecognizerHelper's on-device 2.0f/-80f, tuned down
    since Gemini's vision encoder loses fine strokes under harder contrast)
    -> single JPEG encode at quality ~90-92. Previously: no enhancement at
    all for the Gemini-bound image, plus a wasteful two-pass encode
    (quality 80, then a second decode+re-encode at 95 for EXIF stripping).
  - structureFromImage gained an optional ocrHint: String? parameter — ML
    Kit's already-recognized text is passed in and given to Gemini as a
    cross-check alongside the image. Wired at the MainViewModel call site
    using the same rawText already extracted earlier in the same flow (no
    duplicate ML Kit call added).
  - TextNormalizer: removed ~15 hardcoded word-replacement regex rules
    ("brond tou" -> "bread flour", "ebpuros" -> "all-purpose", etc.) that
    were literal transcriptions of one bad OCR result on a single test card
    and risked corrupting unrelated text containing the same short
    substrings. Fraction normalization, digit/letter splitting, and noise-
    line filtering were untouched.
  - Confidence-triggered auto-retry: new `structureFromImageWithMeta(...)`
    returns `ScanResult(recipe, retried: Boolean)`. Triggers exactly one
    automatic re-send of the same already-processed image bytes when: DTO
    has empty ingredients+steps but is_recipe=true, OR top-level confidence
    == "low", OR more than half of combined ingredient+step items are
    individually "low" confidence (strict `>`, not `>=`; confirmed via
    integer-division edge case: 1 low out of 2 items does not trigger).
    Existing `structureFromImage(...): Recipe` (interface method) delegates
    to this and discards the flag, so ImageCapableStructurer's contract is
    unchanged. Text-only structure() path explicitly excluded — retry is
    image-scan-only. Does NOT interact with or replace the existing
    exception-based HeuristicStructurer fallback chain — orthogonal
    mechanism, only fires on a successfully-parsed-but-low-confidence
    result, never on a thrown exception.
    KNOWN LIMITATION, confirmed via manual device testing this session:
    this only catches cases where Gemini's own self-reported confidence
    flags uncertainty. A confidently-wrong result (bad OCR read, but
    Gemini reports "high"/"medium" confidence on it anyway) does NOT
    trigger this retry and currently ships to the review screen as-is.
    This is expected/inherent — LLM confidence self-reporting is not a
    correctness oracle — and is the reason the manual retry (below) exists
    as the real backstop, not a redundant nice-to-have.
  - Manual "Doesn't look right?" retry: added to the review screen (referred
    to elsewhere in this doc/codebase as ReviewEditScreen — confirm actual
    file name matches before further edits). Two options: "Try again"
    force-resends the same in-memory original image bytes (bypasses
    shouldRetry entirely, forced by the user instead), "Retake photo"
    discards and returns to capture. Original image bytes for the
    currently-in-review scan are held in MainViewModel state
    (_lastScannedImageBytes) and cleared on navigation away from the review
    screen — this is separate from the persisted sourceImagePath used by
    the detail-screen re-scan flow noted above.
  - Debug logging added in GeminiOcrClient (tag "GeminiOcrClient") to
    distinguish automatic confidence-triggered retries from manual
    user-triggered retries in Logcat during testing — no user-facing
    equivalent, by design (see below).
  - Loading-state UX: scan screen shows a generic message by default
    ("Reading your recipe…" or existing equivalent), swaps to a vague
    non-technical message ("Getting a clearer read…" / "Trying again…")
    only while an auto-retry or manual retry is actually in flight. Final
    displayed result is identical regardless of whether a retry occurred —
    no visible indicator, no badge, on the saved recipe. This was a
    deliberate UX choice: exposing "the AI wasn't sure" was judged likely
    to undermine confidence in results that turned out fine.
  - Manual device testing this session: ~70% accuracy on a mixed batch of
    handwritten cards post-fixes, with some hit-or-miss cases where a
    same-photo resend produced a correct result after an initial garbage
    one (motivated the auto-retry, then the manual-retry backstop above).
    Not yet re-tested end-to-end against the full fix set including the
    manual retry control — worth a fresh accuracy pass before drawing
    conclusions about whether the gap to comparable apps (e.g. Flair,
    which appears to use a paid/larger cloud model — unconfirmed,
    inference only, not verified via network inspection) has closed.
- Settings screen (2026-07-30, mentioned but not detailed this session,
  flagging for follow-up): API key verified/unverified status indicator
  added; 4 previously-missing themes added to the theme dropdown. No
  further detail captured on these two in this session — get specifics
  (which themes, how verification status is determined/refreshed) before
  relying on this bullet for anything beyond "these exist now."

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
  card scan, confirm actual response quality on review screen) — UPDATE:
  partially done this session (~70% accuracy observed on handwritten
  cards, pre-manual-retry). Still not tested as a full end-to-end pass with
  every fix from this session in place together — do that before treating
  the OCR accuracy work as closed.
- Voice wake-word timer feature — intentionally deferred, fully decoupled
  subsystem, do not entangle with recipe data model.
- AI-assisted OCR structuring UI polish (confidence highlighting in review
  screen) — data model supports it, UI doesn't use it yet.
- Meal planner, pantry inventory, cooking mode — not started (Phase 3).
- ImagePreprocessorExifTest.kt not re-run against the rewritten
  prepareForUpload() this session — confirm it still passes.
- Confirm consent-flow coverage of the two new retry paths (auto-retry,
  manual "Try again") — see note under Consent flow above.
- README.md and this context doc were significantly out of date as of
  2026-07-29 (README still described a fully on-device Gemini
  Nano/AICore architecture with "no cloud upload" as the privacy claim,
  despite AICore having been removed entirely per the first bullet in this
  file). README rewritten 2026-07-30 to accurately describe the current
  cloud-BYOK-with-consent architecture — review the rewritten version
  against the actual current app behavior before publishing, since it was
  drafted from this context doc and conversation history, not from a fresh
  code read.

Phase 2 (scaling, unit conversion, chef's notes/rating) — COMPLETE.

## Non-goals

- No multi-provider AI abstraction — Gemini only, deliberately, no LiteLLM.
- No sending the original uncompressed image to Gemini — always strip+compress
  first.
- No auto-save on AI structuring — user must review and explicitly save,
  both for first-time scans and re-sends.
- No auto-converting units on existing recipes — AS_WRITTEN is always the
  default, conversion is explicit per-recipe opt-in only.
