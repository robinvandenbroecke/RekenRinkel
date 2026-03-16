# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat werkt

### Oefenflow (gestabiliseerd)
- Uniforme flow voor alle oefentypen
- WORKED_EXAMPLE en GUIDED_PRACTICE worden ondersteund
- Expliciete state machine (SHOWING → PROCESSING → FEEDBACK → ADVANCING)
- Geen vastlopers op eerste oefening
- Automatische advance na feedback

### Start op leeftijd, dan adaptief
1. **Leeftijd bepaalt de start** (5-6 / 7-8 / 9-11 jaar)
2. **Daarna sturen prestaties** het traject:
   - Snelle correcte antwoorden → sneller naar hogere difficulty of volgende CPA-fase
   - Herhaalde fouten → meer steun, lagere difficulty

### Curriculum NL/Vlaanderen
Inhoud afgestemd op:
- **Nederland**: Kerndoelen rekenen-wiskunde PO
- **Vlaanderen**: Minimumdoelen wiskunde lager onderwijs

### Lesstructuur
- **Duur**: 5-10 minuten
- **Aantal**: 8 items per les
- **Opbouw**: 1 warm-up, 4 focus, 2 review, 1 challenge

### Progressie-UI (in ontwikkeling)
- Huidige focus skill met mastery score
- Mastery sterren: ⭐ (50%), ⭐⭐ (70%), ⭐⭐⭐ (90%)

## Wat in ontwikkeling is

- Progressie-UI verder uitwerken
- Spaced review algoritme
- Parent dashboard
- Meer oefentypes

## Flow

```
Onboarding (leeftijd) → Home → Les (8 items) → Beloningen
                              ↓
                        Adaptieve bijsturing
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
