# RekenRinkel V1 Freeze

Lokale Android testapp voor rekenoefeningen, gericht op privétest.

## Wat dit is

Een stabiele V1-versie voor test op eigen apparaten (OnePlus 12, Boox Note Air 4C). Geen productierelease, geen commerciële verspreiding.

## Huidige scope (V1)

- **Leeftijdsstart**: 5-6, 6-8, 8-11 jaar
- **Lesflow**: 8 oefeningen per sessie
- **Oefentypes**: VISUAL_QUANTITY, VISUAL_GROUPS, TYPED_NUMERIC, MISSING_NUMBER
- **Adaptive difficulty**: prestatiegestuurd
- **Lokale opslag**: Room database
- **Rewards**: XP en streak (vereenvoudigd)

## Niet in V1

- Cloud sync
- Accounts
- Play Store
- Monetisatie
- Analytics
- Brede curriculumuitbreiding

## Bouwen

```bash
./gradlew :app:assembleDebug
```

APK verschijnt in `app/build/outputs/apk/debug/`

## Testen

```bash
./gradlew :app:testDebugUnitTest
```

18 tests, alle groen.

## Status

V1 freeze - stabiel voor privétest. V2/publicatie later.
