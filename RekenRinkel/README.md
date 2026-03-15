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

### Gratis
- Getalbeelden tot 5
- Splitsingen tot 10
- Optellen tot 10
- Aftrekken tot 10
- Dubbelen tot 20
- Helften tot 20

### Premium
- Splitsingen tot 20
- Optellen/aftrekken tot 20
- Brug over 10 (optellen/aftrekken)
- Tellen per 2, 5, 10
- Vergelijken tot 100
- Tientallen en eenheden
- Groepjes/vermenigvuldigen
- Tafels van 2, 5, 10

## Technische Stack

- **Platform**: Android
- **Taal**: Kotlin
- **UI**: Jetpack Compose
- **Database**: Room (lokale opslag)
- **Instellingen**: DataStore
- **Architectuur**: MVVM

## Projectstructuur

```
app/src/main/java/com/rekenrinkel/
├── data/
│   ├── local/           # Room database, entities, DAOs
│   ├── datastore/       # DataStore voor instellingen
│   └── repository/      # Repositories voor data toegang
├── domain/
│   ├── model/           # Domeinmodellen
│   └── engine/          # Oefenengine, validatie, sessiebeheer
├── ui/
│   ├── theme/           # Kleuren, typografie
│   ├── components/      # Herbruikbare UI componenten
│   ├── screens/         # Schermen
│   └── viewmodel/       # ViewModels
└── MainActivity.kt
```

## Lokale Opslag

Alle data wordt lokaal opgeslagen op het apparaat:

- **Room database**: `/data/data/com.rekenrinkel/databases/rekenrinkel_database`
  - Profiel
  - Skill voortgang
  - Sessie-resultaten
  - Oefen-resultaten
  
- **DataStore**: `/data/data/com.rekenrinkel/shared_prefs/rekenrinkel_settings.preferences_pb`
  - Profielnaam
  - Gekozen thema
  - Geluid aan/uit
  - Onboarding status
  - Premium vlag (placeholder)

## Free vs Premium

De app is voorbereid op monetisatie via Google Play Billing:

- `Skill.isPremium` markeert premium content
- `SettingsDataStore.premiumUnlocked` is de feature flag
- `PremiumScreen` is de placeholder voor aankoopflow

In V1 is premium nog gesimuleerd via een lokale flag. Koppeling met Google Play Billing komt in een latere versie.

## Bouwen

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## Testen

Unit tests voor oefenlogica:

```bash
./gradlew test
```

## Roadmap

### V1 (huidig)
- [x] Basisarchitectuur
- [x] Alle oefentypes
- [x] Adaptieve engine
- [x] Ouderdashboard
- [x] Free/Premium architectuur

### V2 (gepland)
- [ ] Google Play Billing integratie
- [ ] Meer thema's
- [ ] Geluidseffecten
- [ ] Statistieken exporteren

## Licentie

Copyright 2024 - Alle rechten voorbehouden