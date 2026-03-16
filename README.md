# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - in opbouw  
**Laatste update**: 2026-03-16

## Wat er is (basis aanwezig)

### Profiel en start
- Naam, leeftijd (5-11), thema instellen
- **Placement verplicht** vóór normaal leerpad
- Placement: 5 diagnostische items → startband bepaling
- Rewards systeem: XP, level, streak, badges
- 100% offline, lokale Room database

### Content
- 40+ skills in ContentRepository.kt
- Microskills met CPA-fasen (CONCRETE → PICTORIAL → ABSTRACT)
- Skills gegroepeerd per leeftijdsgroep
- Prerequisites per skill
- Fouttype-remediëring (BOND_ERROR, BRIDGE_10_ERROR, etc.)

### Oefeningen
- ExerciseEngine met didactische generatie
- Oefentypes: TYPED_NUMERIC, VISUAL_GROUPS, MISSING_NUMBER, WORKED_EXAMPLE, GUIDED_PRACTICE
- Difficulty levels 1-5
- Representaties: DOTS, BLOCKS, BOND_MODEL, NUMBER_LINE, SYMBOLS

### Sessie/Les (didactisch opgebouwd)
- LessonEngine: primaire leermotor met fouttype-remediëring
- 10 items per les: Warm-up (2), Focus (4), Review (2), Challenge (2)
- Didactische flow: Worked example → Guided → Independent
- **PATCH 4**: Fouttype-remediëring:
  * BRIDGE_10_ERROR → bond model
  * COMPARE_ERROR → getallenlijn
  * GROUPING_ERROR → arrays
  * etc.
- CPA-fase bepaalt oefeningstype:
  * CONCRETE: veel scaffolding (worked + guided)
  * PICTORIAL: mix (guided + independent)
  * ABSTRACT: zelfstandig (independent)
- Smart shuffle met variatie
- Exit check na sessie

### Voortgang (PATCH 5 & 6)
- SkillProgress per skill: masteryScore (0-100), difficultyTier, CPA-fase
- **PATCH 6**: Mastery sterren (0-3⭐) per skill
- **PATCH 5**: Leerdoelen zichtbaar in UI (focus skill, CPA-fase, voortgang)
- Rewards: XP, level, streak tracking + gekoppeld aan mastery
- ProfielRepository: CRUD operaties
- ProgressRepository: skill resultaten

## Flow (huidige implementatie)

```
App Start
  ↓
Onboarding (naam, leeftijd, thema)
  ↓
Placement (verplicht)
  ↓  5 diagnostische items
  ↓  Bepaalt start cluster
Home
  ↓
Start Les → LessonEngine
  ↓  Warm-up (review)
  ↓  Focus (50% - CPA-afhankelijk)
  ↓  Review (spaced)
  ↓  Challenge (transfer)
  ↓
Exit Check → Mastery update
  ↓
Rewards (XP, badges)
  ↓
Volgende les
```

## Wat in opbouw is

### Leeftijdsadaptatie (PATCH 7 - basis aanwezig)
- Startband via leeftijd: geïmplementeerd
- Placement test: verplicht vóór normaal leerpad
- Placement resultaat bepaalt start cluster
- **PATCH 7**: Leeftijd = bias, niet dictator
- Prestaties overrulen leeftijd na placement
- Dynamische content selectie: in ontwikkeling

### CPA/Singapore Math (gedeeltelijk geïmplementeerd)
- CPA enum aanwezig (CONCRETE, PICTORIAL, ABSTRACT, MIXED_TRANSFER)
- Representatie types gedefinieerd
- Fase-overgangen afdwingbaar in LessonEngine:
  * CONCRETE → PICTORIAL: 60% mastery + 5 attempts
  * PICTORIAL → ABSTRACT: 75% mastery + 8 attempts
  * ABSTRACT → MIXED: 85% mastery

### Mastery engine (PATCH 4, 5, 6 - gedeeltelijk geïmplementeerd)
- SkillProgress tracking: basis aanwezig
- Difficulty adjustment: geïmplementeerd
- CPA-fase tracking: geïmplementeerd
- **PATCH 4**: Fouttype-remediëring geïmplementeerd
- **PATCH 5**: Leerdoelen zichtbaar in UI
- **PATCH 6**: Mastery sterren (0-3⭐) per skill

### Rewards (gedeeltelijk geïmplementeerd)
- XP systeem: basis aanwezig
- Badges: structuur aanwezig, inhoud in opbouw
- Unlock mechanisme: basis aanwezig

### Spaced review (in opbouw)
- Framework aanwezig
- Algoritme: in ontwikkeling
- Prioriteitsscores: gedeeltelijk geïmplementeerd

## Architectuur

### Data lagen
```
UI (Compose) 
  ↓
ViewModels 
  ↓
Repositories (ProfileRepository, ProgressRepository)
  ↓
DAO's (ProfileDao, SkillProgressDao)
  ↓
Room Database
```

### Key files
| Bestand | Functie | Status |
|---------|---------|--------|
| `ContentRepository.kt` | 40+ skill definities | basis aanwezig |
| `ExerciseEngine.kt` | Oefening generatie | basis aanwezig |
| `LessonEngine.kt` | Didactische les structuur | basis aanwezig |
| `SessionEngine.kt` | Sessie flow | basis aanwezig |
| `PlacementEngine.kt` | Startbepaling | geïmplementeerd |
| `ProfileRepository.kt` | Profiel opslag | basis aanwezig |
| `ProgressRepository.kt` | Skill voortgang | basis aanwezig |

## Build

```bash
./gradlew assembleDebug
```

CI via GitHub Actions.

## Vereisten

- Android Studio Hedgehog+
- JDK 17
- `ANDROID_HOME` environment variable

## Data & Privacy

- 100% offline
- Room database (lokaal)
- Geen tracking, geen ads
- Geen account nodig

## Licentie

Copyright 2024 - Privé testbuild
