# RekenRinkel V1

Lokale Android testapp voor rekenoefeningen. Privétestfase.

## Architectuur

Robuuste stage-based lessonflow:
- Expliciete `LessonStepState` machine
- `CompletionStage` pipeline: `NOT_STARTED → RESULT_LOGGED → PROGRESS_UPDATED → REWARDS_APPLIED → READY_TO_ADVANCE → DONE`
- Stage-based error recovery in `LessonViewModel`
- `LessonViewModelFlowTest.kt` als contracttest voor lessonflow

## Testen

```bash
./gradlew testDebugUnitTest
```

Huidige lokale status: **113 unit tests groen**.

## Bouwen

```bash
./gradlew :app:assembleDebug
```

APK staat in `app/build/outputs/apk/debug/`.

## Status

Privétestbuild, niet productierijp. V2/publicatie later.
