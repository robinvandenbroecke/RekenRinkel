# RekenRinkel V1

Lokale Android testapp voor rekenoefeningen. Privétestfase.

## Architectuur

Robuuste stage-based lessonflow:
- Expliciete LessonStepState machine
- CompletionStage pipeline: NOT_STARTED → RESULT_LOGGED → PROGRESS_UPDATED → REWARDS_APPLIED → READY_TO_ADVANCE → DONE
- Stage-based error recovery

## Testen

```bash
./gradlew testDebugUnitTest  # 106 tests
```

## Bouwen

```bash
./gradlew :app:assembleDebug  # APK in app/build/outputs/apk/debug/
```

## Status

Privétest. V2/publicatie later.
