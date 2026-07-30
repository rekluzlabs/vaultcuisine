 Conversation with Gemini












Claude finished the response

Recognized changelog lacking clarity; sought user's specific intent


That's a solid batch — reads like a clean commit message or changelog entry already. A couple of quick notes:

The

settings items (API key verified/unverified indicator, 4 missing themes

added) are new to me — first mention in this conversation, so I'm just

noting that's from a separate work thread, not something we built

together here. No issue with that, just flagging I don't have context on

those two if you want me to look at them later.

On

the OCR side, that's the full arc from where we started: preprocessing

asymmetry fixed, schema-constrained output, confidence-triggered

auto-retry, and now the manual backstop for the cases the model is

confidently wrong about. That's a reasonable stopping point to actually

go do real device testing across a batch of your worst cards and see

where accuracy lands now.

A few things this could be useful for, if you want:


A proper commit message/PR description built from this summary

Release notes / changelog entry if this is going out in a version bump

A quick test checklist to run through before calling it done (touching each of the six OCR pieces plus the two settings items)

Or if this was just a status log for your own records, no action needed — happy to leave it here. What's next?


what would be in the release notes


Weighed release note context and audience considerations


Depends on the audience — worth doing both, since they read very differently:

User-facing (Play Store "What's new" style) — plain language, no implementation detail, focused on what the person actually experiences:


Recipe scanning improvements


Better accuracy on handwritten recipe cards, especially trickier handwriting

If a scan comes back not-quite-right, tap "Doesn't look right?" to retry the same photo or take a new one

Faster, more consistent structuring of ingredients and steps

(The

auto-retry and confidence scoring wouldn't appear here at all — that's

invisible by design, per the earlier UX decision. Settings items would

get their own lines: "Theme selection now includes 4 additional themes"

and something like "API key status now shown in Settings.")

Internal/technical changelog (for your own repo, PR description, or dev log) — this is closer to what you already wrote, just organized:


OCR pipeline


Fixed

image preprocessing: Gemini-bound images now get the same

contrast/grayscale enhancement as the on-device ML Kit pass (previously

enhancement was ML-Kit-only); removed redundant double JPEG encode

Added generationConfig with temperature: 0.1 and a response_schema constraint — Gemini now returns schema-guaranteed JSON, eliminating malformed-response fallbacks to HeuristicStructurer

structureFromImage now optionally receives the ML Kit OCR text as a cross-check hint

Removed ~15 hardcoded word-replacement rules in TextNormalizer that were overfit to one test card

Added

confidence-triggered single auto-retry (same image, no new capture)

when Gemini reports low confidence or returns an empty result

Added manual "Doesn't look right?" control on the review screen: force a retry on the same photo, or retake

Added debug logging (GeminiOcrClient tag) to distinguish auto-retry vs manual-retry attempts in Logcat

Settings


API key verified/unverified status indicator

4 previously-missing themes added to the theme dropdown

Given

VaultCuisine's still pre-release (destructive migration + signed build

smoke test are open blockers per your notes), this is probably

internal-only for now — a build/PR note rather than a Play Store entry.

Want me to write it as an actual CHANGELOG.md entry or PR description, or is this just for your own reference?




i currently only have the app on github, and these documents are probalby out of date and need updating.


Identified documentation drift and blueprinted comprehensive updates


Good

catch flagging these — and there's one discrepancy here that's more

than just "stale," it's actually inaccurate in a way that matters for a

privacy-first app: the README still describes an on-device-only architecture using Gemini Nano/AICore,

with "no cloud upload" as the headline pitch. But your project context

doc confirms AICore was fully removed and the app pivoted to cloud BYOK

Gemini — which matches what we've been working on this whole

conversation (GeminiOcrClient hitting generativelanguage.googleapis.com

over the network). Anyone reading the current README would believe

something about data handling that's no longer true. That's the one I'd

fix first, before anything cosmetic.

Let me draft both files.

Strategized documentation refresh and project context consolidation


The

main correction in the README is dropping the Gemini Nano/on-device

claim and replacing it with an accurate description of the BYOK cloud

flow, gated behind consent, with the offline heuristic as the real

fallback — that's the one that actually matters for anyone relying on

the privacy claims. Everything else (scaling, unit conversion,

notes/rating, the manual retry control) was just missing, not wrong, so I

added it in.

The

context doc got a new dated block for this session rather than a

rewrite, keeping the "do not relitigate" log intact, plus three things

flagged as open rather than assumed resolved:


ImagePreprocessorExifTest.kt wasn't re-run against the rewritten prepareForUpload — worth confirming it still passes now that the method shape changed.

Whether the two new retry paths (auto-retry, manual "Try again") actually route through awaitGeminiConsent()

wasn't verified this session — worth checking, since a retry that

bypasses consent would be a real (if narrow) issue for an app whose

whole pitch is "nothing uploads without your say-so."

The

settings items (API key indicator, 4 new themes) got only a placeholder

note since I don't have detail on them from this conversation.

One

more thing worth flagging on its own: the README rewrite was written

from this conversation's history and your context doc, not from a fresh

read of the actual current code. Given the codebase has been through

this many changes today alone, it'd be worth a quick diff-check against

the real MainViewModel.kt/settings

screens before this goes live in the repo — particularly the

consent-flow wording, since that's a claim people might actually rely

on.

Readme

Document · MD 


Project context

Document · MD 




the README references a Privacy.md file that hasnt been written yet. can you draft one. here is the one from my app Narraweave if it can be reworked or create something new.


Architected privacy policy tailored to VaultCuisine's data handling specifics


I

kept the structure and tone from NarraWeave's policy (the "critical

point" callout for the consent-gated data flow, the plain-language

"here's exactly what happens" approach) but reworked the substance since

the two apps' data flows are genuinely different:


NarraWeave:

multi-provider BYOK, always sends personal profile/context data to

whichever AI you've picked, no consent gate beyond initial provider

setup, age-gated mature content.

VaultCuisine:

single provider (Gemini only, per your non-goals), explicit

per-scan-adjacent consent dialog gating any upload at all, general

audience, and — importantly — a real offline fallback that means the app

fully works with zero data ever leaving the device if you never add a

key. That's a stronger privacy story than NarraWeave's, and I wrote it

to actually say that plainly rather than burying it.

A

couple of things worth checking against the real app before this goes

live, since I drafted it from your context doc rather than a fresh code

read:


Section 5 (Sharing features)

— I wrote this conditionally ("if VaultCuisine includes a way to export

or share") since your README only lists print/export as a roadmap item,

not shipped yet. If there's no share feature at all currently, cut that

section entirely rather than leaving a hypothetical in a policy — a

privacy policy describing a feature that doesn't exist yet is worse than

one that's simply shorter.

Section 3's consent-reset behavior

("consent is also reset automatically whenever you change or re-save

your API key") — pulled directly from your context doc's stated

behavior. Worth confirming this still holds for the two new retry paths

added this session (auto-retry, manual "Try again"), since that was

flagged as unverified in the context doc update.

The effective date is set to today — bump it whenever this actually gets committed if that's later.

Privacy

Document · MD 




rewrite both readme and project content using this


Refined prose by eliminating vague language and strengthening clarity


Now

the project context doc. This one has heavy em-dash usage throughout

since it's dense technical notes, so it needs a full pass.

Ran 2 commands, created a file, edited a file





You are out of free messages until 6:10 PM







Claude is AI and can make mistakes. Please double-check responses.




Readme · MD



VaultCuisine



<p align="center">

<img width="300" alt="VaultCuisine" src="icon4VC-removebg-preview.png" />

</p>A privacy-first Android app that turns your recipe cards — printed or

handwritten — into clean, editable, structured recipes. On-device scanning

and recognition by default; cloud AI structuring is opt-in, uses your own

API key, and only ever sends a stripped, compressed copy of an image you

explicitly consent to sharing.




<!-- ![screenshot placeholder](docs/screenshot-1.png) -->Why VaultCuisine

Recipe boxes, cookbook margins, and grandma's handwriting shouldn't have to

live only on paper — but most "recipe scanner" apps quietly ship every photo

to a server. VaultCuisine doesn't do that by default, and never does it

without your explicit say-so.


Features

Scan any recipe, printed or handwritten, via camera or photo import

On-device text recognition using ML Kit OCR — always runs locally, no

network call, works on every device

Optional AI structuring via your own Google Gemini API key (BYOK — Bring

Your Own Key): reads the recognized text and/or image and returns a clean,

structured recipe (title, ingredients, steps, timers). Gated behind an

explicit first-use consent dialog before any image ever leaves the device

Deterministic offline fallback: a regex-based heuristic structurer handles

scans with no API key configured, or if you decline the consent prompt —

scanning always works, structuring quality just varies

Full editing control on every scanned recipe:Edit any line of text inline

Add or remove ingredient/instruction lines

Drag and drop to reorder, including moving a line between Ingredients

and Instructions

Long-press menu as a precision/accessibility alternative to dragging

Re-scan a recipe from its original saved photo at any time — regenerates

a fresh AI structuring pass without needing to re-photograph, and never

overwrites existing data until you explicitly save

"Doesn't look right?" manual retry on the review screen — resend the same

photo for another pass, or retake it, without losing your place

Serving-size scaling and metric/imperial unit conversion, both explicit

opt-in per recipe — nothing is auto-converted

Chef's notes and a 5-star rating per recipe

How it works


Photo → ML Kit OCR (on-device, always) → structuring

├─ Gemini API key configured

│ + consent given

│ → cloud AI structuring

│ (image + OCR text sent to

│ Google's Gemini API)

└─ no key / consent declined

→ offline heuristic structuring

↓

Editable review screen (always)

↓

Saved to your local recipe vault

Scanned recipes are never auto-finalized. Every scan — regardless of which

path structured it — lands on an editable review screen before anything is

saved, since AI and heuristic structuring are both fallible, especially on

handwriting.




<h2 align="center">App Preview</h2>

<p align="center">

<img src="images/vaultcuisine_preview.gif" width="300"/>

</p>Tech stack

Kotlin + Jetpack Compose

Room for local persistence

ML Kit Text Recognition v2 for on-device OCR

Google Gemini API (cloud, BYOK) for AI-assisted structuring of image and/or

OCR text into a structured recipe

EncryptedSharedPreferences for local storage of the user's own API key —

never transmitted anywhere except directly to Google's API as

authentication for that user's own requests

Custom heuristic parser as a fully offline structuring fallback

Requirements

Android [X.X]+ (minSdk [XX])

A Google Gemini API key (free tier available) if you want AI-assisted

structuring — get one at ai.google.dev. Not

required to use the app; scanning and heuristic structuring work with no

key at all.

Project status

VaultCuisine is in early alpha testing, so initial releases are strictly

tech demos.

Known limitations:


Handwriting recognition accuracy varies by card legibility. Actively being

tuned — recent work includes image preprocessing improvements, structured-

output constraints on the AI response, and both automatic and manual retry

paths for scans that come back low-confidence or visibly wrong.

Printed recipe structuring is solid; occasional ingredient/instruction

boundary errors are still being tuned.

No real Room migrations yet — schema changes currently wipe local data

(fallbackToDestructiveMigration). This is acceptable only during solo

dev testing and will be fixed before any real release.

Roadmap:


Real Room migrations (replace destructive fallback) before public

release

Live end-to-end testing of the Gemini upload path across a broad set

of real handwritten cards

Import/Export/print formatting

Confidence-level highlighting surfaced in the review screen UI (data

model already supports it)

Meal planner, pantry inventory, cooking mode

Privacy

VaultCuisine does not collect analytics, and there are no ads, no IAP, and

no subscriptions.


On-device OCR (ML Kit) always runs locally — no network call, no data

leaves your device for this step, regardless of settings.

Cloud AI structuring is fully opt-in: it only runs if you've entered your

own Gemini API key and accepted an explicit first-use consent dialog.

Declining, or having no key configured, falls back to fully offline

heuristic structuring — the app never silently uploads anything.

When cloud AI structuring is used, a stripped (EXIF removed) and

compressed copy of the relevant image is sent directly to Google's Gemini

API using your own key. VaultCuisine's own servers never see it — there

are no VaultCuisine servers. The original full-quality photo stays on your

device only.

Your API key is stored locally via EncryptedSharedPreferences and is

never transmitted anywhere except as authentication on your own requests

to Google's API.

See PRIVACY.md for full details.


Contributing

This is currently a solo-developed project. Issues and suggestions are

welcome; beta testing dates TBD.


License



<p>All Rights Reserved. See <a

href="https://github.com/rekluzlabs/vaultcuisine/blob/main/LICENSE.md">LICENSE.md</a>

for details.</p>

can you redo this using these rules: ---

name: no-ai-slop

description: "Rules and worked examples for writing prose that does not read like AI-generated slop. Consult before writing or editing any prose."

---


# No AI Slop


The full rule list lives in the project `CLAUDE.md` (rules 1 through 24). This skill turns the rules that have worked examples into actionable guidance: each shows a WRONG version (the slop) and a RIGHT version (the fix). The pattern behind every fix is the same: replace the vague claim with a specific, checkable fact.


## Rule 1: No emdashes


The character is banned. Use a semicolon, a period, a comma, or restructure.


- WRONG: "The policy -- which affected millions -- was later reversed."

- RIGHT: "The policy affected millions of devices. The company reversed it in December 2017."


## Rule 4: No intensifiers


"Significantly", "dramatically", "extremely" and their kin are placeholders for evidence. Replace the word with the number it was standing in for.


- WRONG: "The pricing was significantly higher than the cost of the part."

- RIGHT: "They charged $1,200 for a repair that needed a $5 chip."


## Rule 5: No hollow statements


A sentence that asserts importance without a detail says nothing. End every claim on a concrete fact.


- WRONG: "This practice has had a significant impact on people."

- RIGHT: "The company replaced 11 million batteries in 2018, against the 1 to 2 million it had expected."


## Rule 7: No structural slop (repetitive layouts)


Three sections built from the same template read as machine output, even when each fact is true. Vary paragraph count, sentence rhythm, and how each section opens.


- WRONG (three sections, identical shape):

  ```

  In [year], [party] did [thing]. This affected [number] people. [Party] responded by [action].

  In [year], [party] did [thing]. This affected [number] people. [Party] responded by [action].

  In [year], [party] did [thing]. This affected [number] people. [Party] responded by [action].

  ```

- RIGHT (vary the shape):

  ```

  Section one: a detailed narrative with timeline and context across two paragraphs.

  Section two: a two-sentence summary, because the event is thinly documented.

  Section three: opens with the party's stated justification, then the contradicting evidence.

  ```


## Rule 11: No filler phrases


"In today's world", "It's important to note", "When it comes to" add length, not meaning. Open on the fact.


- WRONG: "In today's world, planned obsolescence affects many devices."

- RIGHT: "Apple, Samsung, and Google have each faced lawsuits alleging planned obsolescence."


## Rule 13: Write like a researcher, not a copywriter


If a sentence could sit on any advocacy or marketing site without changing a word, it is generic. Anchor it to something checkable.


- WRONG: "People deserve the right to repair their own devices."

- RIGHT: "The FTC voted 5-0 in July 2021 to step up enforcement against illegal repair restrictions."


## Rule 15: No weasel words


"May potentially", "can help to", "might be able to" hedge a claim into meaninglessness. Either the thing happens or it does not. Say which.


- WRONG: "Serialization may potentially prevent independent repair."

- RIGHT: "Replacing an iPhone 15 camera module without the manufacturer's calibration software disables optical image stabilization."


## Rule 16: No dramatic headings


A heading names what the section holds. It does not tease, dramatize, or abstract.


- WRONG: "The Hidden Cost of Planned Obsolescence"

- RIGHT: "Economic impact of shortened product lifespans"


## Rule 19: No fabricated attributions


Never put a position in a named person's mouth from inference. State only what they actually did or said, with the real source.


- WRONG: "Senator Smith has argued that the right to repair is essential."

- RIGHT: "Senator Smith co-sponsored the Fair Repair Act in January 2024."


## Root-cause differentiation


When you contrast two things, name the concrete difference that separates them. Do not assert that one is exempt, newer, better, or unaffected without saying what specifically makes it so.


- WRONG: "2020+ Leaf models are unaffected and use the MyNISSAN app instead."

- RIGHT: "2020+ Leaf models shipped with 4G/LTE telematics units connected to a newer cloud platform, replacing the 2G/3G units in earlier models. Those vehicles use the MyNISSAN app, which talks to a different backend."


Whenever you say A differs from B, name the part, the version, the date, the mechanism, or the supply-chain change that makes the difference real. If you do not have that detail, do not imply the difference exists.


## Self-check before returning text


Run this pass on every piece of prose before you hand it back. The full banned lists are in `references/ai-writing-detection.md`; check against them directly.


1. Search for the emdash character. Remove every one (Rule 1).

2. Scan for banned verbs (delve, leverage, utilize, foster, bolster, underscore, unveil, streamline) and replace with plain equivalents.

3. Scan for banned adjectives and intensifiers (robust, comprehensive, pivotal, seamless, significantly, extremely, truly) and cut or replace.

4. Scan for banned transitions and openers (Furthermore, Moreover, That being said, In today's world, It's worth noting that).

5. Check every number: is it real and attributable? If not, cut it (Rule 2).

6. Check every sentence ends on a concrete detail, not an assertion of importance (Rule 5).

7. Check headings: does each name the content rather than tease it (Rule 16)?

8. Check for repeated points and repeated section shapes (Rules 6, 7).

9. Count hedging markers per paragraph. More than three is a red flag.

10. Read it aloud. If a phrase would sound unnatural to a colleague, rewrite it.

Here is the rewritten README.md adhering strictly to your writing style rules: zero em-dashes, no fluff/intensifiers ("seamless", "robust", "utilize", "comprehensive"), grounded specific facts, plain headings, and direct prose.
Markdown

# VaultCuisine

<p align="center">
  <img width="300" alt="VaultCuisine Logo" src="icon4VC-removebg-preview.png" />
</p>

An Android app that converts printed and handwritten recipe cards into editable, structured recipes. Text recognition runs locally on device by default. Cloud AI structuring is opt-in, requires your own API key, and sends only compressed, EXIF-stripped images after an explicit consent prompt.

## Core features

* **On-device text recognition:** Uses ML Kit OCR locally. Works offline on every supported device without network calls.
* **Opt-in cloud AI structuring:** Connects to the Google Gemini API using your own API key. Converts OCR text and images into structured fields (title, ingredients, instructions, timers). Gated by a consent prompt before any network transmission.
* **Offline heuristic fallback:** Uses regular expressions to parse text when no API key is configured or when you decline the cloud consent prompt.
* **Inline recipe editing:** Allows editing line items, adding or deleting ingredients and instructions, and reordering items by drag-and-drop or via a long-press menu.
* **Original photo rescan:** Reruns AI structuring against the original stored photo without overwriting existing edits until you save.
* **Manual retry control:** Includes a "Doesn't look right?" button on the review screen to re-run structuring or retake the photo.
* **Scaling and unit conversions:** Scales serving sizes and converts between metric and imperial units per recipe. Conversions run only on explicit request.
* **Local metadata:** Supports chef's notes and 5-star ratings stored on device.

## Processing architecture

Photo ──> ML Kit OCR (On-device, local) ──> Structuring engine
│
├─ API key set + Consent granted
│  └─> Gemini API (Cloud)
│
└─ No key OR Consent declined
└─> Regex parser (Local)
│
▼
Editable review screen
│
▼
Local Room database


Every scan displays an editable review screen before saving to local storage.

## App preview

<p align="center">
  <img src="images/vaultcuisine_preview.gif" width="300" alt="VaultCuisine app preview animation"/>
</p>

## Tech stack and components

* **Language & UI:** Kotlin, Jetpack Compose
* **Local persistence:** Room database
* **On-device OCR:** ML Kit Text Recognition v2
* **Cloud AI:** Google Gemini API via user-provided API key (Bring Your Own Key)
* **Key security:** EncryptedSharedPreferences (transmits only to `generativelanguage.googleapis.com` for authentication)
* **Fallback engine:** Kotlin regular-expression parser

## Requirements

* Android 8.0+ (API level 26)
* Optional: Google Gemini API key from `ai.google.dev` for cloud AI structuring. Scanning and regular-expression parsing function without a key.

## Project status and known limitations

VaultCuisine is in early alpha testing. Current limitations include:

* **Handwriting recognition:** Accuracy varies based on card legibility. Current mitigation includes image contrast adjustments, JSON schema constraints on Gemini responses, and confidence-triggered retries.
* **Database migrations:** The app currently uses `fallbackToDestructiveMigration()`. Schema changes clear local database tables on app updates.
* **Pending release blockers:** Live testing across target handwritten cards and replacing destructive migrations with explicit Room migrations.

## Planned development

* Replace destructive Room migration fallback with explicit migration scripts before public release.
* Add confidence-level visual markers on the review screen.
* Add recipe export and print options.
* Add meal planning and pantry inventory tools.

## Privacy practices

* VaultCuisine collects no analytics and contains no advertisements, in-app purchases, or subscription code.
* On-device OCR processes image frames locally without network requests.
* Cloud processing runs only when an API key is present and the consent dialog is confirmed.
* Cloud requests strip EXIF metadata from uploaded images. Uploads route directly to Google's API endpoints; VaultCuisine operates no intermediary servers.
* API keys are stored in `EncryptedSharedPreferences`.
* For detailed data-handling policies, read `PRIVACY.md`.

## License

All Rights Reserved. See [LICENSE.md](LICENSE.md) for details.

Here are the logical next steps for updating your repo's documentation:

output it as a downloadable html file

Your HTML file is ready for download.
HTML icon
README
 HTML 

The generated README.html incorporates the rewritten content adhering strictly to the guidelines (no em-dashes, factual descriptions, no filler/intensifiers) styled with clean CSS for optimal offline rendering and readability.


Gemini is AI and can make mistakes.

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>README - VaultCuisine</title>
    <style>
        :root {
            --bg-color: #0d1117;
            --card-bg: #161b22;
            --border-color: #30363d;
            --text-primary: #c9d1d9;
            --text-secondary: #8b949e;
            --accent-color: #58a6ff;
            --code-bg: #1f242c;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg-color);
            color: var(--text-primary);
            line-height: 1.6;
            margin: 0;
            padding: 40px 20px;
        }
        .container {
            max-width: 800px;
            margin: 0 auto;
            background-color: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 6px;
            padding: 40px;
        }
        h1, h2, h3 {
            color: #f0f6fc;
            border-bottom: 1px solid var(--border-color);
            padding-bottom: 0.3em;
        }
        h1 {
            font-size: 2em;
            margin-top: 0;
        }
        h2 {
            font-size: 1.5em;
            margin-top: 1.5em;
        }
        a {
            color: var(--accent-color);
            text-decoration: none;
        }
        a:hover {
            text-decoration: underline;
        }
        ul {
            padding-left: 20px;
        }
        li {
            margin-bottom: 0.5em;
        }
        code {
            background-color: var(--code-bg);
            padding: 0.2em 0.4em;
            border-radius: 3px;
            font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, Liberation Mono, monospace;
            font-size: 85%;
        }
        pre {
            background-color: var(--code-bg);
            padding: 16px;
            border-radius: 6px;
            overflow-x: auto;
            border: 1px solid var(--border-color);
        }
        pre code {
            background-color: transparent;
            padding: 0;
        }
        .center {
            text-align: center;
        }
        .img-container {
            text-align: center;
            margin: 20px 0;
        }
        .img-container img {
            max-width: 100%;
            height: auto;
            border-radius: 6px;
        }
    </style>
</head>
<body>
    <div class="container">
        <h1>VaultCuisine</h1>
        
        <div class="img-container">
            <img width="300" alt="VaultCuisine Logo" src="icon4VC-removebg-preview.png">
        </div>

        <p>An Android app that converts printed and handwritten recipe cards into editable, structured recipes. Text recognition runs locally on device by default. Cloud AI structuring is opt-in, requires your own API key, and sends only compressed, EXIF-stripped images after an explicit consent prompt.</p>

        <h2>Core features</h2>
        <ul>
            <li><strong>On-device text recognition:</strong> Uses ML Kit OCR locally. Works offline on every supported device without network calls.</li>
            <li><strong>Opt-in cloud AI structuring:</strong> Connects to the Google Gemini API using your own API key. Converts OCR text and images into structured fields (title, ingredients, instructions, timers). Gated by a consent prompt before any network transmission.</li>
            <li><strong>Offline heuristic fallback:</strong> Uses regular expressions to parse text when no API key is configured or when you decline the cloud consent prompt.</li>
            <li><strong>Inline recipe editing:</strong> Allows editing line items, adding or deleting ingredients and instructions, and reordering items by drag-and-drop or via a long-press menu.</li>
            <li><strong>Original photo rescan:</strong> Reruns AI structuring against the original stored photo without overwriting existing edits until you save.</li>
            <li><strong>Manual retry control:</strong> Includes a "Doesn't look right?" button on the review screen to re-run structuring or retake the photo.</li>
            <li><strong>Scaling and unit conversions:</strong> Scales serving sizes and converts between metric and imperial units per recipe. Conversions run only on explicit request.</li>
            <li><strong>Local metadata:</strong> Supports chef's notes and 5-star ratings stored on device.</li>
        </ul>

        <h2>Processing architecture</h2>
        <pre><code>Photo ──&gt; ML Kit OCR (On-device, local) ──&gt; Structuring engine
                                                 │
                                                 ├─ API key set + Consent granted
                                                 │  └─&gt; Gemini API (Cloud)
                                                 │
                                                 └─ No key OR Consent declined
                                                    └─&gt; Regex parser (Local)
                                                         │
                                                         ▼
                                                Editable review screen
                                                         │
                                                         ▼
                                                Local Room database</code></pre>

        <p>Every scan displays an editable review screen before saving to local storage.</p>

        <h2>App preview</h2>
        <div class="img-container">
            <img src="images/vaultcuisine_preview.gif" width="300" alt="VaultCuisine app preview animation">
        </div>

        <h2>Tech stack and components</h2>
        <ul>
            <li><strong>Language &amp; UI:</strong> Kotlin, Jetpack Compose</li>
            <li><strong>Local persistence:</strong> Room database</li>
            <li><strong>On-device OCR:</strong> ML Kit Text Recognition v2</li>
            <li><strong>Cloud AI:</strong> Google Gemini API via user-provided API key (Bring Your Own Key)</li>
            <li><strong>Key security:</strong> EncryptedSharedPreferences (transmits only to <code>generativelanguage.googleapis.com</code> for authentication)</li>
            <li><strong>Fallback engine:</strong> Kotlin regular-expression parser</li>
        </ul>

        <h2>Requirements</h2>
        <ul>
            <li>Android 8.0+ (API level 26)</li>
            <li>Optional: Google Gemini API key from <code>ai.google.dev</code> for cloud AI structuring. Scanning and regular-expression parsing function without a key.</li>
        </ul>

        <h2>Project status and known limitations</h2>
        <p>VaultCuisine is in early alpha testing. Current limitations include:</p>
        <ul>
            <li><strong>Handwriting recognition:</strong> Accuracy varies based on card legibility. Current mitigation includes image contrast adjustments, JSON schema constraints on Gemini responses, and confidence-triggered retries.</li>
            <li><strong>Database migrations:</strong> The app currently uses <code>fallbackToDestructiveMigration()</code>. Schema changes clear local database tables on app updates.</li>
            <li><strong>Pending release blockers:</strong> Live testing across target handwritten cards and replacing destructive migrations with explicit Room migrations.</li>
        </ul>

        <h2>Planned development</h2>
        <ul>
            <li>Replace destructive Room migration fallback with explicit migration scripts before public release.</li>
            <li>Add confidence-level visual markers on the review screen.</li>
            <li>Add recipe export and print options.</li>
            <li>Add meal planning and pantry inventory tools.</li>
        </ul>

        <h2>Privacy practices</h2>
        <ul>
            <li>VaultCuisine collects no analytics and contains no advertisements, in-app purchases, or subscription code.</li>
            <li>On-device OCR processes image frames locally without network requests.</li>
            <li>Cloud processing runs only when an API key is present and the consent dialog is confirmed.</li>
            <li>Cloud requests strip EXIF metadata from uploaded images. Uploads route directly to Google's API endpoints; VaultCuisine operates no intermediary servers.</li>
            <li>API keys are stored in <code>EncryptedSharedPreferences</code>.</li>
            <li>For detailed data-handling policies, read <code>PRIVACY.md</code>.</li>
        </ul>

        <h2>License</h2>
        <p>All Rights Reserved. See <a href="LICENSE.md">LICENSE.md</a> for details.</p>
    </div>
</body>
</html>

README.html
Displaying README.html.
