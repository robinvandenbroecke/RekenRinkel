# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Werkwijze

### Start op leeftijd, dan adaptief
1. **Leeftijd bepaalt de start** (5-6 / 7-8 / 9-11 jaar)
2. **Daarna sturen prestaties** het traject:
   - Snelle correcte antwoorden → sneller naar hogere difficulty of volgende CPA-fase
   - Herhaalde fouten → meer steun, lagere difficulty, of terug naar sterkere representatie
   - Zwakke skills komen terug via spaced review
   - Sterke kinderen stromen sneller door dan de leeftijdsnorm
   - Zwakkere kinderen vallen terug naar eerdere conceptuele lagen

### Curriculum NL/Vlaanderen
Inhoud afgestemd op:
- **Nederland**: Kerndoelen rekenen-wiskunde PO (referentieniveaus 1F/1S)
- **Vlaanderen**: Minimumdoelen wiskunde lager onderwijs

Clusters:
- Getalbegrip (subitizing, bonds, plaatswaarde)
- Bewerkingen (optellen, aftrekken, brug over 10)
- Patronen en relaties (doubles, skip counting)
- Vermenigvuldigen (groepjes, arrays, tafels)
- Breuken en redeneren (vanaf 8-11 jaar)

### CPA-gestuurde representaties
Per skillcluster: Concrete → Pictorial → Abstract → Transfer
- Fase-vooruit na voldoende beheersing (60%/75%/90% mastery + streak)
- Terugval naar sterkere representatie bij 3+ opeenvolgende fouten
- Geen abstract-first bij jonge of zwakke gebruikers

### Lesstructuur
- **Duur**: 5-10 minuten
- **Aantal**: 8 items per les
- **Opbouw**: 1 warm-up, 4 focus, 2 review, 1 challenge
- **Focus**: 1 skill of microcluster per les

### Progressie-UI
- Huidige focus skill met mastery score (0-100%)
- CPA-fase indicator
- Mastery sterren: ⭐ (50%), ⭐⭐ (70%), ⭐⭐⭐ (90%)
- "Mastered" bij 90%
- Review indicator voor skills die lang geleden zijn geoefend

### Rewards (leerpad-gericht)
- Skill mastered badge
- CPA-fase badges
- Mastery sterren (0-3)
- Cluster badges (foundation, arithmetic)
- Review consistentie badge
- XP secundair (correct, snel, streak)

### Placement
- **Optioneel**, niet verplicht
- Beschikbaar als diagnostische tool voor ouders
- Voor herijking na lange tijd of schoolse evaluatie

## Flow

```
Onboarding (leeftijd) → Home → Les (8 items, ~7 min) → Beloningen
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
