# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Actieve ontwikkeling  
**Laatste update**: 2026-03-16

## Wat werkt nu

### Profiel en start
- Naam, leeftijd (5-11), thema instellen
- Startband bepaald door leeftijd (FOUNDATION/EARLY_ARITHMETIC/EXTENDED)
- Rewards systeem (XP, level, streak, badges)
- 100% offline, lokale Room database

### Content
- 30+ skills in ContentRepository.kt
- Skills gegroepeerd per leeftijdsgroep:
  - **Foundation (5-6)**: subitize, counting, bonds, splits
  - **Early Arithmetic (6-8)**: add/sub tot 20, bridge over 10
  - **Extended (8-11)**: groups, tables, place value, fractions
- Prerequisites per skill

### Oefeningen
- ExerciseEngine genereert oefeningen per skill
- Oefentypes: TYPED_NUMERIC, VISUAL_GROUPS, MISSING_NUMBER, etc.
- Difficulty levels 1-5
- Distractors gegenereerd per fouttype

### Sessie/Les
- LessonEngine: 10 items per les
- Fases: Warm-up (2), Focus (4), Review (2), Challenge (2)
- Smart shuffle met variatie
- Exit check na sessie

### Voortgang
- SkillProgress per skill: masteryScore (0-100), difficultyTier
- Rewards: XP, level, streak tracking
- ProfielRepository: CRUD operaties
- ProgressRepository: skill resultaten

## Wat in ontwikkeling is

### CPA-fasen (Concrete-Pictorial-Abstract)
- Enum en velden zijn aanwezig in skill configs
- Representatie types gedefinieerd
- Integratie in UI/exercise rendering volgt

### Fouttype-detectie
- ErrorType enum aanwezig
- detectErrorType() functie in ExerciseEngine
- Koppeling aan remediëring volgt

### Adaptiviteit
- Difficulty adjustment logica aanwezig
- Representatie selectie framework aanwezig
- Volledige integratie volgt

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
| Bestand | Functie |
|---------|---------|
| `ContentRepository.kt` | Skill definities, 30+ configs |
| `ExerciseEngine.kt` | Oefening generatie per skill |
| `LessonEngine.kt` | Les structuur (10 items) |
| `SessionEngine.kt` | Sessie flow, XP berekening |
| `ProfileRepository.kt` | Profiel + Rewards opslag |
| `ProgressRepository.kt` | Skill voortgang opslag |

## Build

```bash
./gradlew assembleDebug
```

CI via GitHub Actions (artifacts beschikbaar).

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
