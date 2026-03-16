# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat werkt

### Robuuste lessonflow (gecentraliseerd in ViewModel)
- **Expliciete completion modes**:
  - `DIRECT_CONTINUE`: worked example, skip (geen feedback)
  - `FEEDBACK_THEN_ADVANCE`: gewone antwoorden, guided practice
- **Harde completion guard**: elke oefening maximaal één keer verwerkt
- **Fail-safe error handling**: fouten zichtbaar in UI, geen silent hang
- **Dubbele submit protection**: extra submits worden genegeerd

### Oefentypen
- **WORKED_EXAMPLE**: "Begrepen! Verder" → direct door
- **GUIDED_PRACTICE**: Normale antwoordflow met hint
- **Normale antwoorden**: Validatie → feedback → auto-advance
- **Skip**: Direct door, blokkeert nooit

### Error handling
- Fouten in progress/rewards/logging blokkeren de flow niet
- Zichtbare error overlay met "Verder" knop
- Veilige fallback naar volgende oefening of home

### Start op leeftijd
- Leeftijd bepaalt startniveau (5-6 / 7-8 / 9-11 jaar)
- Daarna adaptief op basis van prestaties

## Wat in ontwikkeling is

- Spaced review algoritme
- Parent dashboard
- Meer oefentypes

## Flow

```
Onboarding (leeftijd) → Home → Les (fail-safe flow) → Beloningen
```

## Build & Test

```bash
# Build
./gradlew :app:compileDebugKotlin

# Tests
./gradlew :app:testDebugUnitTest
```

CI: https://github.com/robinvandenbroecke/RekenRinkel/actions

## Data & Privacy

- 100% offline, Room database
- Geen tracking, geen ads

## Licentie

Copyright 2024 - Privé testbuild
