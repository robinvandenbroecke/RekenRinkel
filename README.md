# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat werkt

### Placement (verplicht)
- 6-8 diagnostische items bij eerste start
- Didactisch correcte antwoordopties per item
- Analyse per vaardigheidscluster (number sense, arithmetic, etc.)
- Bepaalt start cluster, focus skills, start-CPA-fase
- Geen normaal leerpad zonder placement

### LessonEngine (enige normale leerflow)
- 10 items per les: Warm-up (2), Focus (4), Review (2), Challenge (2)
- Didactische flow: Worked example → Guided → Independent
- SessionEngine is alleen helper, geen parallel curriculum

### CPA-fases (persistent per skill)
- CONCRETE → PICTORIAL: 60% mastery + 5 attempts
- PICTORIAL → ABSTRACT: 75% mastery + 8 attempts  
- ABSTRACT → MIXED: 85% mastery
- Fase wordt per skill opgeslagen in database

### Fouttype-remediëring
- BRIDGE_10_ERROR → bond model
- COMPARE_ERROR → getallenlijn
- GROUPING_ERROR → arrays
- COUNTING_ERROR → subitizing
- PLACE_VALUE_ERROR → blokken

### Progressie-UI (echte data)
- Huidige focus skill met mastery score (0-100%)
- CPA-fase indicator per skill
- 0-3 sterren per skill (50%/70%/90%)
- Volgende skill met lock/unlock status

### Rewards
- XP voor: correct, snel (<3s), streak, mastered
- Mastery sterren: ⭐ (50%), ⭐⭐ (70%), ⭐⭐⭐ (90%)
- "Les Master" badge voor 80%+ accuracy

## Wat in ontwikkeling is

- Spaced review algoritme
- Uitgebreider parent dashboard
- Meer oefentypes

## Flow

```
App Start → Onboarding → Placement (verplicht) → Home → LessonEngine → Rewards
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
