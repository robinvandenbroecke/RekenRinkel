# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat werkt

### Start op basis van leeftijd (nieuw)
- Geen verplichte placement-test meer
- Leeftijd bepaalt startband en initiële skills:
  - **5-6 jaar**: Foundation (subitizing, telling, number bonds 5)
  - **7-8 jaar**: Early Arithmetic (optellen/aftrekken tot 20, brug over 10)
  - **9-11 jaar**: Extended (tafels, plaatswaarde, breuken)
- Direct naar leerpad na onboarding
- Placement optioneel beschikbaar als oudertool

### Curriculum NL/Vlaanderen
Skills afgestemd op:
- **Nederland**: Kerndoelen rekenen-wiskunde PO (referentieniveaus 1F/1S)
- **Vlaanderen**: Minimumdoelen wiskunde lager onderwijs

Domeinen:
- Getalbegrip (number sense, bonds, plaatswaarde)
- Bewerkingen (optellen, aftrekken, brug over 10)
- Patronen en relaties (doubles, skip counting)
- Vermenigvuldigen (groepjes, arrays, tafels)
- Breuken en redeneren (vanaf 8-11 jaar)

### Adaptieve progressie
- Mastery per skill (0-100%)
- Automatische difficulty adjustment (+1 na 3 correct, -1 na 2 fout)
- CPA-fase advance op basis van prestaties:
  - CONCRETE → PICTORIAL: 60% mastery + 5 attempts
  - PICTORIAL → ABSTRACT: 75% mastery + 8 attempts
  - ABSTRACT → MIXED: 85% mastery

### LessonEngine
- 10 items per les: Warm-up (2), Focus (4), Review (2), Challenge (2)
- Didactische flow: Worked example → Guided → Independent
- Fouttype-remediëring (terug naar sterkere representatie bij fouten)

### Progressie-UI
- Huidige focus skill met mastery score
- CPA-fase indicator
- Mastery sterren: ⭐ (50%), ⭐⭐ (70%), ⭐⭐⭐ (90%)
- "Mastered" status bij 90%

### Rewards (mastery-first)
- Mastery sterren per skill
- "Skill Mastered" badge
- XP secundair (correct, snel, streak, mastered bonus)

## Wat in ontwikkeling is

- Spaced review algoritme
- Uitgebreider parent dashboard
- Meer oefentypes

## Flow

```
App Start → Onboarding (leeftijd) → Home → LessonEngine → Rewards
                ↓
        Optionele placement (oudertool)
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
