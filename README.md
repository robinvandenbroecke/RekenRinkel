# RekenRinkel

Educatieve rekenapp voor kinderen van ongeveer 6 jaar.

**Status**: Technische basis aanwezig, nog niet productierijp

## Build

```bash
./gradlew assembleDebug
```

Vereisten:
- Android Studio Hedgehog+
- JDK 17
- `ANDROID_HOME` of `local.properties` met `sdk.dir`

## Status Componenten

| Component | Status | Opmerking |
|-----------|--------|-----------|
| ContentRepository | Basis werkt | Typed rules, geen string maps meer |
| ExerciseEngine | Basis werkt | Genereert oefeningen, verfijning mogelijk |
| ExerciseValidator | Basis werkt | Valideert antwoorden |
| SessionEngine | Basis werkt | Adaptieve logica aanwezig, niet optimaal getest |
| Room Database | Aanwezig | Entiteiten gedefinieerd, migraties niet getest |
| DataStore | Aanwezig | Instellingen opslag |
| Response Time | Aanwezig | Timer werkt, integratie niet breed getest |
| UI Schermen | Aanwezig | Functioneel, geen gebruikerstests |
| Tests | Gedeeltelijk | Unit tests, geen integratietests |
| Geluid | Placeholder | UI toggle, geen audio |
| Premium | Placeholder | Lokale flag, geen billing |

## Typed Didactische Regels

Vervanging van `Map<String, String>` door sealed class:

```kotlin
sealed class DidacticRule {
    data class ValueRange(val min: Int, val max: Int) : DidacticRule()
    data class RequireBridgeThroughTen(val required: Boolean) : DidacticRule()
    data class ExactStep(val step: Int) : DidacticRule()
    // ...
}
```

## Vaardigheden

18 skills met prerequisites, verdeeld in gratis/premium.

## Architectuur

- MVVM met Jetpack Compose
- Room voor lokale data
- DataStore voor instellingen

## Beperkingen

- Geen geluidseffecten
- Geen Play Store billing
- Geen integratietests
- Geen breed gebruikersgetest
- SessionEngine adaptiviteit niet gevalideerd
- Response time tracking niet gevalideerd in echte sessies

## Licentie

Copyright 2024