# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - in opbouw  
**Laatste update**: 2026-03-16

## Wat er is

### Profiel en start (basis aanwezig)
- Naam, leeftijd (5-11), thema instellen
- Startband bepaald door leeftijd (FOUNDATION/EARLY_ARITHMETIC/EXTENDED)
- Rewards systeem: XP, level, streak (gedeeltelijk geïmplementeerd)
- 100% offline, lokale Room database

### Content (basis aanwezig)
- 30+ skills in ContentRepository.kt
- Skills gegroepeerd per leeftijdsgroep:
  - **Foundation (5-6)**: subitize, counting, bonds, splits
  - **Early Arithmetic (6-8)**: add/sub tot 20, bridge over 10
  - **Extended (8-11)**: groups, tables, place value, fractions
- Prerequisites per skill (basis aanwezig)

### Oefeningen (basis aanwezig)
- ExerciseEngine genereert oefeningen per skill
- Oefentypes: TYPED_NUMERIC, VISUAL_GROUPS, MISSING_NUMBER
- Difficulty levels 1-5
- Distractors gegenereerd

### Sessie/Les (basis aanwezig)
- LessonEngine: 10 items per les
- Fases: Warm-up (2), Focus (4), Review (2), Challenge (2)
- Smart shuffle met variatie
- Exit check na sessie (nog niet volledig coherent)

### Voortgang (basis aanwezig)
- SkillProgress per skill: masteryScore (0-100), difficultyTier
- Rewards: XP, level, streak tracking
- ProfielRepository: CRUD operaties
- ProgressRepository: skill resultaten

## Wat in opbouw is

### Leeftijdsadaptatie (in opbouw)
- Startband via leeftijd: basis aanwezig
- Placement test: nog niet volledig geïmplementeerd
- Dynamische content selectie: in ontwikkeling

### CPA/Singapore Math (in opbouw)
- CPA enum aanwezig (CONCRETE, PICTORIAL, ABSTRACT, MIXED_TRANSFER)
- Representatie types gedefinieerd
- Koppeling aan UI/exercise rendering: nog niet volledig coherent

### Mastery engine (gedeeltelijk geïmplementeerd)
- SkillProgress tracking: basis aanwezig
- Difficulty adjustment logica: aanwezig
- Remediëring op basis van fouttypes: in opbouw

### Rewards (gedeeltelijk geïmplementeerd)
- XP systeem: basis aanwezig
- Badges: structuur aanwezig, inhoud in opbouw
- Unlock mechanisme: basis aanwezig

### Microskills (basis aanwezig)
- 30+ skills gedefinieerd
- Prerequisites: aanwezig
- Micro-less structuur (10 items): basis aanwezig

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
| `ContentRepository.kt` | 30+ skill definities | basis aanwezig |
| `ExerciseEngine.kt` | Oefening generatie | basis aanwezig |
| `LessonEngine.kt` | Les structuur | basis aanwezig |
| `SessionEngine.kt` | Sessie flow | basis aanwezig |
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
