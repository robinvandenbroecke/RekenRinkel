# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar, gebaseerd op Singapore CPA methode.

**Status**: Actieve ontwikkeling - Mastery-based learning engine  
**Versie**: 2.0 (Adaptive Learning Testbuild)

## Pedagogische Basis

RekenRinkel volgt de **Singapore CPA methode**:
- **C**oncreet → **P**icturaal → **A**bstract
- Progressieve mastery tracking
- Spaced review van zwakke skills
- Adaptieve moeilijkheid per skill

## Leeftijdsindeling & Skills

| Leeftijd | Skills |
|----------|--------|
| 5-6 jaar | Subitizing, telling, meer/minder, number bonds 5-10, vormen |
| 6-8 jaar | Rekenen tot 20, brug over 10, doubles/halves, skip counting |
| 8-11 jaar | Plaatswaarde, vermenigvuldigen, tafels, breuken, problem solving |

**30+ skills** met prerequisites en progressieve unlocking.

## Mastery Engine

### Per Skill Tracking
- Mastery score 0-100
- Difficulty tier 1-5
- Streak tracking (correct/incorrect)
- Gemiddelde response tijd
- Error type analyse
- Last representation gebruikt

### Difficulty Adjustment
- **+1 difficulty**: 3 correct op rij + snel genoeg
- **-1 difficulty**: 2 fouten op rij
- Prerequisites opnieuw aanbieden bij herhaalde fouten

### Lesstructuur (10 items)
1. **Warm-up** (2 items): Makkelijke review
2. **Focus** (4 items): 50% kernskill
3. **Review** (2 items): 30% spaced review zwakke skills
4. **Challenge** (2 items): 20% moeilijker/nieuw

## Beloningssysteem

### XP Verdienen
| Actie | XP |
|-------|-----|
| Juist antwoord | 10 XP |
| Snel juist (<3s) | 15 XP |
| Streak bonus (3+) | 5 XP |
| Skill mastered | 25 XP |

### Badges
- 🏆 **Skill Mastered**: Per skill behaald
- 🔥 **Hot Streak**: 10 juist op rij
- ⚡ **Snelheidsgans**: Snel antwoord
- 📅 **Daily Streak**: X dagen achter elkaar

### Progressie
- Level ups op basis van totale XP
- Daily streak tracking
- Personal bests per skill

## Build

### Vereisten
- Android Studio Hedgehog+ (of nieuwer)
- JDK 17
- `ANDROID_HOME` omgevingsvariabele

### Lokale Debug Build

```bash
./gradlew assembleDebug
```

**Output**: `app/build/outputs/apk/debug/app-debug.apk`

### CI Build (GitHub Actions)

Automatische build bij push naar `main`.

**Workflow**: `.github/workflows/build-debug-apk.yml`

**Download**:
1. Ga naar **Actions** tab in GitHub
2. Selecteer meest recente run
3. Download `rekenrinkel-debug-apk` artifact

## Installatie

### OnePlus 12 / Android Telefoon
```bash
adb install app-debug.apk
```

### BOOX Note Air 4C (E-ink)
1. APK overzetten via USB
2. "Onbekende bronnen" accepteren
3. **E-Ink Modus** inschakelen in Instellingen

## E-Ink Modus

- Minimale animaties
- Hoog contrast (zwart/wit)
- Grote touch targets
- Geen kleurafhankelijkheid

## Data & Privacy

- **100% offline** - geen cloud
- Alle voortgang lokaal opgeslagen
- Geen tracking, geen ads
- Geen account nodig

### Wat wordt opgeslagen
- Profiel (naam, leeftijd, theme)
- Skill mastery per oefening
- XP, streak, badges
- Les voortgang
- Instellingen

## Reset Opties

Via Instellingen:
- **Reset Voortgang**: Wis skill voortgang
- **Reset Profiel**: Volledige reset (als nieuwe installatie)

## Technisch

### Architectuur
- MVVM met Jetpack Compose
- Room database (lokaal)
- DataStore (instellingen)
- Kotlin Coroutines & Flow

### Key Components
| Component | Functie |
|-----------|---------|
| `LessonEngine` | Bouwt adaptive lessen (50/30/20 mix) |
| `ExerciseEngine` | Genereert oefeningen per type |
| `ExerciseValidator` | Valideert antwoorden |
| `SessionViewModel` | Beheert les flow & state |
| `ContentRepository` | 30+ skill definities |

### Oefentypes
- Visual quantity (stippen)
- Visual groups (splitsen)
- Missing number
- Multiple choice
- Typed numeric
- Compare numbers
- Sequence (patronen)
- Number line click
- Number bonds

## Test Checklist

### Functioneel
- [ ] Les start correct
- [ ] Oefeningen komen niet direct dubbel
- [ ] Antwoorden worden correct gescoord
- [ ] Feedback verschijnt (800ms)
- [ ] Auto-advance werkt
- [ ] Skip overslaat 1 oefening
- [ ] Wit scherm niet mogelijk
- [ ] Resultaatscherm toont XP/badges

### Data
- [ ] XP blijft behouden
- [ ] Skill mastery wordt opgeslagen
- [ ] Streak telt correct
- [ ] Badges blijven behouden

### Flow
- [ ] Les doorloopt alle 10 items
- [ ] Na laatste: resultaatscherm
- [ ] Opnieuw starten werkt
- [ ] Daily streak update

## Roadmap

- [x] Mastery engine
- [x] Adaptive difficulty
- [x] Lesson phases (warm-up/focus/review/challenge)
- [x] XP & badge systeem
- [x] 30+ skills
- [ ] Geluidseffecten
- [ ] Uitgebreidere word problems
- [ ] Parent dashboard verbeteringen
- [ ] Meer representaties (bar model)

## Licentie

Copyright 2024 - Privé testbuild

---

**Laatste update**: 2026-03-15 - Adaptive Learning Engine v2.0