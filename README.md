# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar, gebaseerd op Singapore CPA methode.

**Status**: Actieve ontwikkeling - Mastery-based adaptive learning  
**Versie**: 2.0 (Microskills + CPA-fases)

## Wat deze app doet

RekenRinkel leert kinderen rekenen via **microskills** met een duidelijke **concreet → picturaal → abstract** progressie.

### Start: Leeftijd + Placement
- Kind voert naam en leeftijd (5-11) in
- Korte placement test (6-10 items) bepaalt startniveau
- Leeftijdsgroep bepaalt eerste skill cluster:
  - **5-6 jaar**: foundation (subitizing, telling, bonds)
  - **6-8 jaar**: early arithmetic (optellen/aftrekken, brug over 10)
  - **8-11 jaar**: extended (vermenigvuldigen, breuken, redeneren)

### Skillstructuur: Microskills met CPA
Elke skill doorloopt expliciete fasen:
1. **CONCRETE**: Fysieke objecten, stippen, blokjes
2. **PICTORIAL**: Bond model, getallenlijn, arrays
3. **ABSTRACT**: Symbolische notatie
4. **MIXED_TRANSFER**: Contextoefeningen

Voorbeeld "optellen tot 10":
- Concrete: blokjes samenvoegen
- Picturaal: bond model invullen
- Abstract: 3 + 4 = ?
- Transfer: verhaaltje met optelling

### Lesstructuur: Micro-lessen (5-10 min)
Elke les bevat exact 10 items in vaste volgorde:
1. **Warm-up** (2 items): Makkelijke review van bekende skills
2. **Focus** (4 items): Kernskill waar de les om draait
3. **Review** (2 items): Spaced repetition van zwakke skills
4. **Challenge** (2 items): Iets moeilijker of nieuwe skill

### Adaptiviteit: Per skill + fouttype
Het systeem past zich aan per skill op basis van:
- **Mastery score** (0-100): Hoe goed beheerst
- **Difficulty tier** (1-5): Hoe moeilijk de items
- **Streak**: 3 correct = moeilijker, 2 fout = makkelijker
- **Fouttypes**: Herhaalde bridge-10 fout → terug naar bonds
- **Response tijd**: <3s = snel, >10s = moeite
- **CPA-fase**: Alleen abstract als concrete/picturaal sterk is

### Representaties: Passend bij de skill
Niet elk oefentype bij elke skill. De content bepaalt:
- **Subitizing**: alleen visual quantity (stippen)
- **Number bonds**: part-part-whole model, missing number
- **Optellen**: groepjes → bond model → symbolisch
- **Tafels**: arrays → herhaald optellen → abstract
- **Vergelijken**: getallenlijn → compare symbolen

### Leerpad: Zichtbaar en coherent
Kind ziet:
- Huidige module (bv. "Number Bonds")
- Lesdoel vandaag (bv. "Splitsen tot 10")
- Progressie: 7/10 items
- Volgende skill (bv. "Optellen tot 10")
- Mastery sterren per skill (0-3)

### Beloningen: Gekoppeld aan mastery
Geen willekeurige muntjes. Beloningen voor:
- **Les voltooid**: +XP, resultaatscherm met samenvatting
- **Skill mastered**: 🏆 badge + unlock volgende skill
- **Streak**: 🔥 3/5/10 juist op rij
- **Review terugkomst**: XP bonus voor oude skill opnieuw goed
- **Nieuwe module vrijgespeeld**: celebratie + nieuwe skills zichtbaar

## Mastery Engine Details

### Per Skill Tracking
```
SkillProgress {
  skillId: "arithmetic_add_10"
  masteryScore: 0-100           // Beheersingsniveau
  currentDifficultyTier: 1-5    // Moeilijkheidsgraad
  currentCpaPhase: CONCRETE/PICTORIAL/ABSTRACT
  correctCount / incorrectCount
  streakCorrect / streakIncorrect
  averageResponseTimeMs
  errorTypeSummary: {BRIDGE_10_ERROR: 3, BOND_ERROR: 1}
  lastPracticedAt
  masteredAt
}
```

### Difficulty Adjustment Regels
- **+1 tier**: 3 correct op rij + gem. <5s
- **-1 tier**: 2 fouten op rij
- **+1 CPA-fase**: 5 correct op rij in huidige fase
- **-1 CPA-fase** (remediëring): 3+ fouten van zelfde type

### Fouttype-Remediëring
- **BRIDGE_10_ERROR** → Terug naar number bonds 10
- **PLACE_VALUE_ERROR** → Terug naar tientallen/eenheden concreet
- **GROUPING_ERROR** → Terug naar groepjes/arrays
- **COMPARE_ERROR** → Terug naar getallenlijn

### Spaced Review Algoritme
Skills komen terug voor review op basis van:
- Mastery < 90: na 1 dag
- Mastery 90-95: na 3 dagen
- Mastery 95-99: na 7 dagen
- Fout gemaakt: volgende les al review

## Content: 40+ Microskills

### Foundation (5-6 jaar)
- Subitizing 3, 5 (concreet)
- Telling (concreet)
- Meer/minder/evenveel (concreet)
- Number bonds 5 (concreet → picturaal)
- Number bonds 10 (picturaal → abstract)
- Vormen, patronen

### Early Arithmetic (6-8 jaar)
- Optellen tot 10 (concreet → picturaal → abstract)
- Aftrekken tot 10 (idem)
- Number bonds 20
- Optellen/aftrekken tot 20
- Brug over 10 (concreet → picturaal → abstract)
- Dubbelen/helften
- Skip counting 2, 5, 10

### Extended (8-11 jaar)
- Vergelijken tot 100
- Plaatswaarde (tientallen/eenheden)
- Groepjes (vermenigvuldigen concreet)
- Arrays
- Tafels 2, 5, 10, 3, 4
- Eenvoudige breuken
- Meerstapsproblemen
- Redeneeropgaven

## Build & Installatie

### Vereisten
- Android Studio Hedgehog+
- JDK 17
- `ANDROID_HOME` gezet

### Debug Build
```bash
./gradlew assembleDebug
```

### CI (GitHub Actions)
Automatisch bij push naar `main`. Download artifact in Actions tab.

### E-ink (BOOX)
E-Ink modus in Instellingen: minimale animaties, hoog contrast.

## Data & Privacy

- **100% offline**, geen cloud
- Room database (lokaal)
- Geen tracking, geen ads
- Geen account nodig

### Wat wordt opgeslagen
- Profiel (naam, leeftijd, theme, placement status)
- SkillProgress per skill (mastery, difficulty, CPA-fase, fouttypes)
- Session results (voor review algoritme)
- XP, streak, badges
- Instellingen

## Reset Opties
- **Reset voortgang**: Wis skill data, behoud profiel
- **Reset profiel**: Alles wissen, opnieuw placement

## Technisch

### Architectuur
- MVVM + Jetpack Compose
- Room (lokaal)
- DataStore (instellingen)
- Coroutines + Flow

### Key Components
| Component | Functie |
|-----------|---------|
| `ContentRepository` | 40+ skill definities met CPA, error types, representaties |
| `LessonEngine` | Micro-lessen (warm-up/focus/review/challenge) |
| `ExerciseEngine` | Genereert oefeningen per skill, difficulty, representatie |
| `SessionEngine` | Beheert les flow, state machine |
| `ExerciseValidator` | Valideert antwoorden, detecteert fouttypes |
| `ProgressRepository` | SkillProgress opslag, mastery updates |

## Roadmap

### Klaar
- [x] Leeftijd + placement systeem
- [x] Microskills structuur
- [x] CPA-fases expliciet
- [x] Error type tracking
- [x] Micro-lessen (4 fases)
- [x] Adaptive difficulty per skill
- [x] Spaced review algoritme
- [x] Mastery badges

### In Ontwikkeling
- [ ] Fouttype-gestuurde remediëring UI
- [ ] Representatie-switching (concreet ↔ picturaal)
- [ ] Exit check mastery assessment
- [ ] Ouder dashboard met foutanalyse

### Gepland
- [ ] Geluidseffecten
- [ ] Uitgebreidere word problems
- [ ] Personal bests tracking
- [ ] Meer tafels en breuken

## Licentie

Copyright 2024 - Privé testbuild

---

**Laatste update**: 2026-03-16 - Microskills + CPA-fases engine
