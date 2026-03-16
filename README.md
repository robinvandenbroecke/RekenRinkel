# RekenRinkel

Educatieve wiskunde-app voor kinderen van 5-11 jaar.

**Status**: Privétestfase - niet productierijp  
**Laatste update**: 2026-03-16

## Wat werkt

### Oefenflow (volledig ViewModel-gestuurd)
- **Alle advance-logica centraal in LessonViewModel**
- UI is puur presentational (alleen tonen en input verzamelen)
- Expliciete state machine: SHOWING → PROCESSING → FEEDBACK → ADVANCING
- Feedback-delay en auto-advance volledig in ViewModel
- Geen vastlopers op eerste oefening

### Oefentypen
- **WORKED_EXAMPLE**: Direct verder zonder validatie
- **GUIDED_PRACTICE**: Normale antwoordflow met hint
- **Normale antwoorden**: Validatie, feedback, auto-advance
- **Skip**: Direct door zonder blokkeren

### Start op leeftijd, dan adaptief
1. **Leeftijd bepaalt de start** (5-6 / 7-8 / 9-11 jaar)
2. **Daarna sturen prestaties** het traject

### Curriculum NL/Vlaanderen
Inhoud afgestemd op Nederlandse kerndoelen en Vlaamse minimumdoelen.

### Lesstructuur
- **Duur**: 5-10 minuten
- **Aantal**: 8 items per les

## Wat in ontwikkeling is

- Progressie-UI verder uitwerken
- Spaced review algoritme
- Parent dashboard

## Flow

```
Onboarding (leeftijd) → Home → Les (8 items, ViewModel-gestuurd) → Beloningen
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
