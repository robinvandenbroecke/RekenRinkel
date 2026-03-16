# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat er is

### Kernarchitectuur
- **Placement verplicht** vóór normaal leerpad (PATCH 2)
- **LessonEngine** als EXCLUSIEVE leermotor (PATCH 3)
- **CPA-fases** sturen representatie en oefeningstype
- **Fouttype-remediëring** gekoppeld aan representaties
- **Rewards** gekoppeld aan mastery (XP + sterren)
- 100% offline, lokale Room database

### Placement (PATCH 2)
- 6-8 diagnostische items bij eerste start
- Echte analyse van antwoorden en response tijd
- Bepaalt start cluster, focus skills, CPA-fase
- Leeftijd = bias, placement resultaat = startniveau
- Geen normaal leerpad zonder placement

### LessonEngine (EXCLUSIEVE leermotor - PATCH 3)
- 10 items per les: Warm-up (2), Focus (4), Review (2), Challenge (2)
- Didactische flow: Worked example → Guided → Independent
- **SessionEngine** is helper/uitgelijnd, geen parallel curriculum
- Geen fallback - LessonEngine bepaalt alles

### CPA-fases (afdwingbaar)
- CONCRETE → PICTORIAL: 60% mastery + 5 attempts
- PICTORIAL → ABSTRACT: 75% mastery + 8 attempts
- ABSTRACT → MIXED: 85% mastery
- Fase bepaalt oefeningstype:
  * CONCRETE: worked + guided
  * PICTORIAL: guided + independent
  * ABSTRACT: independent

### Fouttype-remediëring
- BRIDGE_10_ERROR → bond model
- COMPARE_ERROR → getallenlijn
- GROUPING_ERROR → arrays
- COUNTING_ERROR → subitizing
- PLACE_VALUE_ERROR → blokken/tientallen

### Zichtbaar in UI
- Huidige focus skill en CPA-fase
- Mastery progress (0-100%)
- 0-3 sterren per skill
- Volgende skill indicator
- Review nodig meldingen

### Rewards
- XP gekoppeld aan: correct, snel, streak, mastered
- Mastery sterren: ⭐ (50%), ⭐⭐ (70%), ⭐⭐⭐ (90%)
- Badges voor: skill mastered, streak, speed

## Wat in ontwikkeling is

### In opbouw
- Spaced review algoritme
- Meer uitgebreide parent dashboard
- Extra oefentypes (woordproblemen, etc.)
- Geluidseffecten

### Gedeeltelijk geïmplementeerd
- Fouttype-analyse (framework aanwezig, volledige integratie volgt)
- Adaptieve difficulty (basis werkt, finetuning volgt)
- Remediëring triggers (logica aanwezig, UI koppeling volgt)

## Flow

```
App Start
  ↓
Onboarding
  ↓
Placement (verplicht)
  ↓  5 items → start cluster
Home
  ↓
Start Les
  ↓  LessonEngine
  ↓  ├─ Warm-up (review)
  ↓  ├─ Focus (50%, CPA-afhankelijk)
  ↓  ├─ Review (spaced)
  ↓  └─ Challenge (transfer)
Exit Check
  ↓  Mastery update
Rewards
  ↓  XP + sterren
Volgende les
```

## Architectuur

```
UI (Compose)
  ↓
ViewModels
  ↓
Repositories
  ↓
LessonEngine (primair)
  ↓  ├─ ExerciseEngine
  ↓  └─ SessionEngine (helper)
  ↓
DAO's → Room
```

## Build

```bash
./gradlew assembleDebug
```

CI via GitHub Actions.

## Vereisten

- Android Studio Hedgehog+
- JDK 17
- `ANDROID_HOME`

## Data & Privacy

- 100% offline
- Geen tracking, geen ads
- Geen account nodig

## Licentie

Copyright 2024 - Privé testbuild
