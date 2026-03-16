# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat werkt

### Robuuste oefen-afhandeling (FAIL-SAFE)
- **Harde completion guard**: elke oefening maximaal één keer verwerkt
  - `currentlyCompletingExerciseId` voor lopende verwerking
  - `handledExerciseIds` set voor definitief afgehandelde oefeningen
- **Expliciete completion status**: NOT_STARTED → RESULT_SAVED → PROGRESS_UPDATED → REWARDS_UPDATED → READY_TO_ADVANCE
- **Expliciete completion modes**: DIRECT_CONTINUE (worked/skip), FEEDBACK_THEN_ADVANCE (normaal)
- **Expliciete ERROR state**: interruptiestatus, niet terug naar SHOWING
- **Failure stages**: RESULT_LOGGING, PROGRESS_UPDATE, REWARD_UPDATE, STATE_UPDATE, ADVANCE, UNKNOWN
- **Failure context**: exerciseId, type, index, stage, timestamp
- **Context-aware recovery**: recovery afhankelijk van failure stage en exercise type

### Debug logging
- Uitgebreide logging in completion flow
- Zichtbaar in logcat: start, stages, failures, recovery actions
- Essentieel voor traceerbaarheid van "hang"-bugs

### Oefentypen failure paths
- **WORKED_EXAMPLE**: geen validator/retry, altijd door naar volgende
- **GUIDED_PRACTICE**: antwoord/feedback-flow, veilige recovery
- **Normale antwoorden**: standaard response path
- **Skip**: direct en veilig, geen antwoord-failure flow

### Error handling
- Fouten zichtbaar in UI met duidelijke melding
- "Verder" knop voor veilige recovery
- Geen silent failures

## Wat in ontwikkeling is

- Spaced review algoritme
- Parent dashboard
- Meer oefentypes

## Build & Test

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

CI: https://github.com/robinvandenbroecke/RekenRinkel/actions

## Data & Privacy

- 100% offline, Room database
- Geen tracking, geen ads

## Licentie

Copyright 2024 - Privé testbuild
