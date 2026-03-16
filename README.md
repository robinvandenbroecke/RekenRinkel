# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat werkt

### Robuuste oefen-afhandeling (FAIL-SAFE)
- **Harde completion guard**: elke oefening wordt maximaal één keer verwerkt
- **Expliciete completion modes**:
  - `DIRECT_CONTINUE`: voor worked example en skip (geen feedback)
  - `FEEDBACK_THEN_ADVANCE`: voor normale antwoorden (feedback + wacht)
- **Fail-safe error handling**: fouten in progress/rewards logging blokkeren de flow niet
- **No silent failures**: exceptions resulteren in duidelijke foutmelding + veilige doorloop

### ViewModel-gestuurde flow
- Alle advance-logica centraal in LessonViewModel
- UI is puur presentational
- Expliciete state machine: SHOWING → PROCESSING → FEEDBACK → ADVANCING

### Oefentypen
- **WORKED_EXAMPLE**: Direct verder zonder validatie
- **GUIDED_PRACTICE**: Normale antwoordflow met hint
- **Normale antwoorden**: Validatie, feedback, auto-advance
- **Skip**: Direct door zonder blokkeren

### Start op leeftijd, dan adaptief
- Leeftijd bepaalt de start (5-6 / 7-8 / 9-11 jaar)
- Daarna sturen prestaties het traject

## Wat in ontwikkeling is

- Progressie-UI verder uitwerken
- Spaced review algoritme
- Parent dashboard

## Flow

```
Onboarding (leeftijd) → Home → Les (8 items, fail-safe) → Beloningen
```

## Build

```bash
./gradlew :app:compileDebugKotlin
```

CI: https://github.com/robinvandenbroecke/RekenRinkel/actions

## Data & Privacy

- 100% offline, Room database
- Geen tracking, geen ads

## Licentie

Copyright 2024 - Privé testbuild
