# RekenRinkel V1 Status

## Architectuur

**Actief**: robuuste stage-based lessonflow

- `LessonStepState`: `IDLE, SHOWING, PROCESSING, FEEDBACK, ADVANCING, ERROR, COMPLETED`
- `CompletionStage`: `NOT_STARTED → RESULT_LOGGED → PROGRESS_UPDATED → REWARDS_APPLIED → READY_TO_ADVANCE → DONE`
- `FailureStage`: `VALIDATION, RESULT_LOGGING, PROGRESS_UPDATE, REWARD_UPDATE, ADVANCE, UNKNOWN`

## Testdekking

- **113 unit tests groen**
- `LessonViewModelFlowTest.kt`: contracttests voor gewone antwoorden, worked example, guided practice, skip, recovery en dubbele side effects
- Repository-tests voor profiel- en progress-persistentie toegevoegd

## Build

- Debug APK bouwt lokaal
- GitHub Actions workflow bouwt tests + debug APK op push naar `main`

## Scope

V1 privétestbuild voor eigen apparaten. Niet productierijp, geen publicatie- of monetisatiescope.
