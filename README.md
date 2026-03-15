# RekenRinkel

Educatieve rekenapp voor kinderen van ongeveer 6 jaar. Een stabiele, lokaal werkende Android-app voor korte, speelse rekensessies.

## Kenmerken

- **Volledig lokaal**: Geen cloud, geen account, geen advertenties, geen tracking
- **Adaptief**: Moeilijkheid past zich aan op basis van resultaten
- **Didactisch verantwoord**: Geen triviale opgaven, echte brug-over-10, visuele getalbeelden
- **Kindvriendelijk**: Grote knoppen, weinig tekst, leuke thema's

## Thema's

- 🦕 Dinosaurussen
- 🚗 Auto's  
- 🚀 Ruimte

## Vaardigheden

### Gratis (Free)
| Skill | Beschrijving | Prerequisites |
|-------|--------------|---------------|
| foundation_number_images_5 | Getalbeelden tot 5 | - |
| foundation_splits_10 | Splitsingen tot 10 | Getalbeelden |
| arithmetic_add_10 | Optellen tot 10 | Getalbeelden |
| arithmetic_sub_10 | Aftrekken tot 10 | Optellen tot 10 |
| patterns_doubles | Dubbelen tot 20 | Optellen tot 10 |
| patterns_halves | Helften tot 20 | Dubbelen |

### Premium
| Skill | Beschrijving | Prerequisites |
|-------|--------------|---------------|
| foundation_splits_20 | Splitsingen tot 20 | Splitsingen tot 10 + Optellen tot 10 |
| arithmetic_add_20 | Optellen tot 20 | Optellen tot 10 + Splitsingen |
| arithmetic_sub_20 | Aftrekken tot 20 | Aftrekken tot 10 + Optellen tot 20 |
| arithmetic_bridge_add | Brug over 10 (optellen) | Optellen tot 20 + Splitsingen |
| arithmetic_bridge_sub | Brug over 10 (aftrekken) | Aftrekken tot 20 + Brug over 10 add |
| patterns_count_2 | Tellen per 2 | Dubbelen + Splitsingen |
| patterns_count_5 | Tellen per 5 | Tellen per 2 |
| patterns_count_10 | Tellen per 10 | Tellen per 5 |
| advanced_compare_100 | Vergelijken tot 100 | Optellen tot 20 + Tellen per 10 |
| advanced_place_value | Tientallen en eenheden | Vergelijken + Tellen per 10 |
| advanced_groups | Groepjes/vermenigvuldigen | Dubbelen + Tellen per 2 |
| advanced_table_2 | Tafel van 2 | Groepjes + Tellen per 2 |
| advanced_table_5 | Tafel van 5 | Groepjes + Tellen per 5 + Tafel 2 |
| advanced_table_10 | Tafel van 10 | Groepjes + Tellen per 10 + Tafel 5 |

## Architectuur

### Tech Stack
- **Platform**: Android (minSdk 24, targetSdk 34)
- **Taal**: Kotlin
- **UI**: Jetpack Compose
- **Database**: Room (lokale opslag)
- **Instellingen**: DataStore
- **Architectuur**: MVVM

### Projectstructuur
```
app/src/main/java/com/rekenrinkel/
├── data/
│   ├── datastore/       # DataStore voor instellingen
│   ├── local/           # Room database, entities, DAOs
│   └── repository/      # Repositories
├── domain/
│   ├── content/         # ContentRepository met skill configuraties
│   ├── engine/          # ExerciseEngine, SessionEngine, ExerciseValidator
│   └── model/           # Domeinmodellen
├── ui/
│   ├── theme/           # Kleuren, typografie
│   ├── components/      # Herbruikbare UI componenten
│   ├── screens/         # Schermen
│   └── viewmodel/       # ViewModels
└── MainActivity.kt
```

## Belangrijke Componenten

### ContentRepository
Centrale configuratie voor alle skills:
- Prerequisites (leerlijn)
- Didactische regels
- Toegestane oefentypes
- Free/Premium flags
- Generator configuratie

### ExerciseEngine
Genereert didactisch correcte oefeningen:
- Geen +0, -0 of triviale gevallen
- Echte brug-over-10 waar van toepassing
- Visuele ondersteuning bij lage difficulty
- Progressieve moeilijkheidsopbouw

### ExerciseValidator
Consistente validatie over alle oefentypes:
- Normalisatie van input
- Numerieke en tekstuele validatie
- Geen false positives

### SessionEngine
Adaptieve sessieopbouw:
- 50% Focus skill
- 30% Review zwakke vaardigheden
- 20% Challenge/nieuwe skill
- Respects prerequisites
- Premium gating

## Lokale Opslag

Alle data wordt lokaal opgeslagen:

### Room Database
- Profiel
- Skill voortgang (mastery scores)
- Sessie-resultaten
- Oefen-resultaten

### DataStore
- Profielnaam
- Gekozen thema
- Geluid aan/uit
- Onboarding status
- Premium vlag (placeholder)

## Free vs Premium

De app is voorbereid op monetisatie:

- `ContentRepository` markeert skills als free/premium
- `SettingsDataStore.premiumUnlocked` is de feature flag
- `SettingsScreen` heeft een toggle voor testing
- `SessionEngine` filtert premium skills als niet unlocked

## Bouwen

### Vereisten
- Android Studio Hedgehog of nieuwer
- JDK 17
- Android SDK 34

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

## Testen

### Unit Tests
```bash
./gradlew test
```

Test coverage:
- `ExerciseEngineTest`: Generatie van oefeningen
- `ExerciseValidatorTest`: Validatie logica
- `ContentRepositoryTest`: Skill configuraties en prerequisites

### Test Focus
- Prerequisites en skill unlocking
- Bridge-over-10 klopt echt
- Getalbeelden tonen correct aantal
- Geen negatieve uitkomsten
- Geen resultaten boven skillgrenzen
- Skip counting consistent
- Distractors zonder duplicates
- Validator matcht generator output
- Premium skills correct gated

## Roadmap

### V1 (huidig) ✅
- [x] ContentRepository met skill configuraties
- [x] ExerciseEngine met didactisch correcte oefeningen
- [x] SessionEngine met adaptieve logica
- [x] ExerciseValidator met consistente validatie
- [x] Room + DataStore implementatie
- [x] Alle UI schermen
- [x] Free/Premium architectuur
- [x] Uitgebreide tests

### V2 (gepland)
- [ ] Google Play Billing integratie
- [ ] Meer thema's
- [ ] Geluidseffecten
- [ ] Statistieken exporteren

## Bijdragen

Deze app is ontwikkeld als persoonlijk project. De code is beschikbaar voor referentie en leren.

## Licentie

Copyright 2024 - Alle rechten voorbehouden

---

## Wat Werkt Nu

1. **ContentRepository**: Expliciete skill configuraties met prerequisites
2. **ExerciseEngine**: Didactisch correcte oefeningen
3. **SessionEngine**: Adaptieve sessies met mastery tracking
4. **ExerciseValidator**: Consistente validatie
5. **SettingsDataStore**: Werkende sound toggle en premium flag
6. **MainViewModel**: Integratie met alle repositories
7. **Tests**: Uitgebreide coverage

## Wat Is Placeholder

1. **Geluid**: UI toggle werkt, maar er zijn nog geen geluidseffecten
2. **Premium Billing**: Alleen lokale feature flag, geen echte aankoop flow
3. **Parent Dashboard**: Basis UI, kan uitgebreider met grafieken
4. **Animaties**: Basis feedback, kan verfijnder