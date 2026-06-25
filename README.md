# AuroraScan — Offline-Core MVP

A privacy-first Android document scanner, built from the AuroraScan blueprint.
This repository contains the **offline-core MVP**: a working end-to-end vertical
slice of the product.

> "AuroraScan" is a placeholder name. It is a CamScanner-class product but must
> not copy CamScanner branding, icons, wording, layouts, or proprietary
> algorithms.

## What this MVP does

The full pipeline from the blueprint, wired together and runnable:

```
ML Kit Document Scanner  →  Internal storage (Room + files)  →  ML Kit OCR (WorkManager)  →  Searchable PDF export  →  Android Sharesheet
```

- **Scan** multi-page documents with the ML Kit Document Scanner (gallery import allowed).
- **Persist** originals into app-internal storage with SHA-256 checksums and
  atomic write-then-rename; document + page rows are written in one Room transaction.
- **OCR** every page in the background with ML Kit Text Recognition v2, stored as
  a normalized-coordinate block/line/word tree (re-runnable without losing data).
- **Search** across document titles and recognized text (offline, title-weighted).
- **Export** a searchable PDF — visible scanned image plus an invisible, selectable
  OCR text layer — and share it via the Android Sharesheet.
- **Favorite** and soft-delete documents.

## Architecture

Single Gradle module (`:app`) with clean internal layering that mirrors the
blueprint's modular design, so it can be split into modules later without
rework:

| Layer | Package | Notes |
|---|---|---|
| UI | `ui/` | Jetpack Compose + Material 3, Navigation Compose |
| Domain | `domain/usecase/` | Thin use cases over the repository |
| Data | `data/` | Room (`local/`), file storage (`files/`), repository |
| Engines | `engine/` | `scan`, `ocr`, `pdf` — **interfaces + first-release impls** |
| Work | `work/` | WorkManager OCR job (Hilt-injected) |

Every processing component sits behind an interface (`DocumentScanEngine`,
`OcrEngine`, `PdfEngine`) and is bound in `di/BindingsModule`. Swapping in a
CameraX scanner, PaddleOCR/ONNX engine, or a cloud sync engine later is a
one-line change there — no feature code touched (blueprint §8).

### Key stack
Kotlin · Jetpack Compose / Material 3 · Hilt · Room · WorkManager · DataStore ·
Coil · ML Kit Document Scanner + Text Recognition · Android `PdfDocument` ·
Kotlinx Serialization. Versions are centralized in `gradle/libs.versions.toml`.

## Building

Requires the **Android SDK** (API 34) and JDK 17+.

```bash
# point Gradle at your SDK
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew :app:assembleDebug      # build the debug APK
./gradlew :app:testDebugUnitTest  # run JVM unit tests
```

The APK is written to `app/build/outputs/apk/debug/`.

> Note: this scaffold was generated in an environment without the Android SDK,
> so the APK has not been compiled here. The Gradle wrapper, version catalog,
> manifest, and sources are complete and ready for a standard Android build.

## Tested device target

Primary test device per the blueprint: Samsung Galaxy S22 (Android).
`minSdk 24`, `targetSdk 34`.

## Not yet implemented (next slices from the blueprint)

Crop/filter editors and enhancement recipes (OpenCV), OCR review/correction UI,
folders & tags, annotations & signatures, password-protected PDF, app lock
(BiometricPrompt), recycle bin UI, and the FastAPI cloud-sync release. The
data model and engine seams for these already exist.
