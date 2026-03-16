# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - in opbouw  
**Laatste update**: 2026-03-16

## Wat er is (basis aanwezig)

### Profiel en start
- Naam, leeftijd (5-11), thema instellen
- Startband bepaald door leeftijd (FOUNDATION/EARLY_ARITHMETIC/EXTENDED)
- Placement engine met 6-10 diagnostische items
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
- LessonEngine: 10 items per les
- Fases: Warm-up (2), Focus (4), Review (2), Challenge (2)
- Didactische flow: Worked example → Guided → Independent
- Smart shuffle met variatie
- Exit check na sessie

### Voortgang
- SkillProgress per skill: masteryScore (0-100), difficultyTier, CPA-fase
- Rewards: XP, level, streak tracking
- ProfielRepository: CRUD operaties
- ProgressRepository: skill resultaten

## Wat in opbouw is

### Leeftijdsadaptatie (in opbouw)
- Startband via leeftijd: basis aanwezig
- Placement test: geïmplementeerd, moet nog volledig geïntegreerd
- Dynamische content selectie: in ontwikkeling

### CPA/Singapore Math (in opbouw)
- CPA enum aanwezig (CONCRETE, PICTORIAL, ABSTRACT, MIXED_TRANSFER)
- Representatie types gedefinieerd
- Fase-overgangen: framework aanwezig, volledige integratie volgt

### Mastery engine (gedeeltelijk geïmplementeerd)
- SkillProgress tracking: basis aanwezig
- Difficulty adjustment: geïmplementeerd
- Remediëring op basis van fouttypes: framework aanwezig

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
