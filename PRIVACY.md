# VaultCuisine Privacy Policy

**Rekluz Labs**
Effective Date: July 30, 2026 · Version 1.0

At Rekluz Labs, we respect your privacy and are committed to protecting
your personal data. This Privacy Policy explains how VaultCuisine handles
information when you use the app.

## 1. Information Stored on Your Device

VaultCuisine stores everything you create locally on your device —
**Rekluz Labs does not collect, receive, or store this data on any
server**, because Rekluz Labs has no servers. This includes:

- Photos of recipe cards you scan, saved full-quality and app-private
- Recognized and structured recipe content: titles, ingredients, steps,
  timers, notes, ratings
- Your scaling and unit-conversion preferences per recipe
- Your Gemini API key, if you've chosen to add one (see Section 3)

All of this remains on your device under your control, in a local database
(Room/SQLite), unless you explicitly delete the app or clear its data.

## 2. On-Device Text Recognition — Always Local

Every scan starts with on-device text recognition (ML Kit OCR). This step
runs entirely on your device, with no network call and no data transmitted
anywhere, regardless of any other setting in the app. If you never add a
Gemini API key, scanning and recipe structuring both continue to work
end-to-end without a single byte leaving your device — accuracy on tricky
handwriting will simply be lower than with AI-assisted structuring turned
on.

## 3. Optional Cloud AI Structuring (Your Own Gemini API Key)

VaultCuisine can optionally use Google's Gemini API to improve structuring
accuracy, especially on handwritten cards. This is **opt-in and
user-controlled** at every step:

> **Critical point:** Cloud AI structuring only runs if you've entered your
> own Gemini API key **and** explicitly accepted a first-use consent
> dialog before any image is sent. If you decline, or haven't added a key,
> VaultCuisine automatically falls back to fully offline structuring — the
> app never silently uploads anything.

When cloud AI structuring is active:

- A **stripped (EXIF-removed) and compressed copy** of the relevant image
  is sent directly from your device to Google's Gemini API, authenticated
  with your own API key.
- The **original full-quality photo never leaves your device** — only the
  transient, stripped copy generated for that specific request is sent, and
  it is not retained by VaultCuisine after the request completes.
- Rekluz Labs' own infrastructure never sees this data in transit — there
  is no Rekluz Labs server in the path. The request goes straight from your
  device to Google's API.
- Google handles this data according to their own privacy policy:
  [policies.google.com/privacy](https://policies.google.com/privacy). Please
  review it if you have questions about how Google processes API requests.
- You can revoke this at any time by removing your API key in Settings.
  Consent is also reset automatically whenever you change or re-save your
  API key, so you're asked to confirm again.

## 4. Data Storage and Retention

All your data — recipes, photos, preferences, and your API key — is stored
locally on your device. **Rekluz Labs has no servers, databases, or
backups of your information.** If you delete the app or clear its data
through your device settings, this information is permanently removed. The
only exception is if you've explicitly exported or shared a recipe
yourself (see Section 5).

## 5. Sharing Features

If VaultCuisine includes a way to export or share a recipe (for example,
through your device's native share sheet to another app), you're always in
control of what's shared and to whom. Rekluz Labs does not facilitate or
monitor this sharing. Once content leaves the app this way, the receiving
app or platform's own terms and privacy policy apply — review those if you
have concerns about content you've chosen to share.

## 6. Your Choices and Control

You have complete control over your data at all times:

- Add, change, or remove your Gemini API key at any time in Settings —
  removing it immediately reverts all future scans to offline structuring.
- Decline the AI consent prompt on any individual scan; this doesn't
  disable AI structuring permanently, it just falls back for that scan.
- Edit, correct, or delete any scanned recipe — nothing is ever
  auto-finalized without your review.
- Delete individual recipes or clear all app data via your device's app
  settings at any time.

## 7. Children's Privacy

VaultCuisine is a general-audience recipe organizing tool with no
mature-content gating and is not directed at collecting personal
information from children. Because all data is stored locally on your
device, Rekluz Labs has no ability to remotely access, retrieve, or delete
data from any device. A parent or guardian can remove all app data at any
time by deleting the app or clearing its data through device settings.

## 8. Security

VaultCuisine is designed with privacy and security as core principles:

- Storing all data locally rather than on remote servers minimizes exposure
  to network-based attacks on your recipe data.
- Your Gemini API key is stored using `EncryptedSharedPreferences`, backed
  by your device's Android Keystore.
- VaultCuisine includes no analytics, advertising, or tracking libraries —
  there are no third-party services monitoring your activity.
- There are no ads, no in-app purchases, and no subscriptions.

## 9. Changes to This Privacy Policy

We may update this Privacy Policy from time to time to reflect changes in
the app or for other operational, legal, or regulatory reasons. Material
changes will be reflected by updating the "Effective Date" above. Continued
use of VaultCuisine after changes become effective constitutes acceptance
of the updated policy.

## 10. Contact Us

If you have any questions about this Privacy Policy or VaultCuisine's
privacy practices, please contact:

**Rekluz Labs**
Email: [rekluzlabs@gmail.com](mailto:rekluzlabs@gmail.com)

---

*This Privacy Policy was last updated on July 30, 2026.*
