# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat werkt

### Robuuste lessonflow met expliciete completion stages
- **Expliciete CompletionStage**: NOT_STARTED → RESULT_LOGGED → PROGRESS_UPDATED → REWARDS_APPLIED → READY_TO_ADVANCE → DONE
- **Stage-based recovery**: recovery actie afhankelijk van hoe ver afhandeling gekomen is
- **Idempotente side effects**: dubbele calls worden veilig genegeerd per stage
- **DONE guard**: een oefening kan maar één keer volledig afgerond worden
- **Failure stages**: RESULT_LOGGING, PROGRESS_UPDATE, REWARD_UPDATE, STATE_UPDATE, ADVANCE
- **Debug logging**: volledige traceerbaarheid in logcat met [COMPLETION], [FAILURE], [RECOVERY] prefix
- **Rijke failure context**: completion stage en flags voor betere recovery beslissingen
- **Expliciete progress/reward failure handling**: failures worden gelogd, niet stilgeslikt

### Oefentype-specifieke afhandeling
- **WORKED_EXAMPLE**: direct door zonder validatie, veilige failure path
- **GUIDED_PRACTICE**: validatie + feedback, veilige recovery
- **Normale antwoorden**: standaard response path met feedback
- **Skip**: direct en veilig, blokkeert nooit

### Regressie-checks
- ✅ Eerste oefening kan altijd afronden (ook na fout)
- ✅ Worked example gaat direct door
- ✅ Guided practice valideert en gaat door
- ✅ Skip blokkeert niet
- ✅ Geen dubbele submit (DONE guard)
- ✅ Geen dubbele XP (REWARDS_APPLIED check)
- ✅ Geen dubbele progress update (PROGRESS_UPDATED check)
- ✅ Geen dubbele result logging (RESULT_LOGGED check)
- ✅ Geen wit scherm na les
- ✅ Result screen blijft werken
- ✅ Stage-based recovery werkt

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

## Status

Privétestfase, nog niet productierijp.

## Licentie

Copyright 2024 - Privé testbuild
