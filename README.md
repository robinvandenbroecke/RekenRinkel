# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat werkt

### Robuuste lessonflow met expliciete error handling
- **Expliciete ERROR state**: interruptiestatus bij fouten, niet terug naar SHOWING
- **Failure recovery**: context-aware recovery zonder dubbele afhandeling
- **Completion guard**: elke oefening maximaal één keer verwerkt
- **Failure stages**: RESULT_LOGGING, PROGRESS_UPDATE, REWARD_UPDATE, STATE_UPDATE, ADVANCE
- **Debug logging**: volledige traceerbaarheid in logcat

### Oefentype-specifieke afhandeling
- **WORKED_EXAMPLE**: direct door, veilige failure path
- **GUIDED_PRACTICE**: validatie + feedback, veilige recovery
- **Normale antwoorden**: standaard response path
- **Skip**: direct en veilig, blokkeert nooit

### Regressie-checks
- ✅ Eerste oefening kan altijd afronden
- ✅ Worked example gaat direct door
- ✅ Guided practice valideert en gaat door
- ✅ Skip blokkeert niet
- ✅ Geen dubbele submit
- ✅ Geen dubbele XP
- ✅ Geen wit scherm na les
- ✅ Result screen blijft werken

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
