# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat werkt

### Robuuste foutafhandeling (semantisch veilig)
- **Expliciete ERROR state**: interruptiestatus, niet terug naar SHOWING
- **Failure stages**: RESULT_LOGGING, PROGRESS_UPDATE, REWARD_UPDATE, STATE_UPDATE, ADVANCE, UNKNOWN
- **Failure context**: exerciseId, type, index, stage, timestamp
- **Context-aware recovery**: recovery afhankelijk van failure stage
- **Geen silent failures**: fouten altijd zichtbaar in UI

### Completion flow
- **Expliciete modes**: DIRECT_CONTINUE (worked/skip), FEEDBACK_THEN_ADVANCE (normaal)
- **Harde completion guard**: elke oefening maximaal één keer
- **Stapsgewijze afhandeling**: result → progress → rewards → state → advance

### Oefentypen
- **WORKED_EXAMPLE**: "Begrepen! Verder" → direct door
- **GUIDED_PRACTICE**: Normale antwoordflow met hint
- **Normale antwoorden**: Validatie → feedback → auto-advance
- **Skip**: Direct door, blokkeert nooit

### Error recovery
- Fouten in progress/rewards/logging blokkeren flow niet
- Zichtbare error overlay met "Verder" knop
- Slimme recovery gebaseerd op failure stage

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
