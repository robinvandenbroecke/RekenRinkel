# RekenRinkel

Educatieve rekenapp voor kinderen van ongeveer 6 jaar.

**Status**: Actieve ontwikkeling - V1 iteratie 2

## Build Status

✅ **Reproduceerbaar**: Gradle wrapper toegevoegd  
⚠️ **Vereist**: `ANDROID_HOME` environment variable of `local.properties` met `sdk.dir`

```bash
./gradlew assembleDebug
```

## Functionele Status

| Component | Status | Opmerkingen |
|-----------|--------|-------------|
| ContentRepository | ✅ Werkt | Alle 18 skills geconfigureerd |
| ExerciseEngine | ✅ Werkt | Didactisch correcte oefeningen |
| ExerciseValidator | ✅ Werkt | Consistente validatie |
| SessionEngine | ⚠️ Gedeeltelijk | Basis adaptief, nog verfijning nodig |
| Room Database | ✅ Werkt | Profiel, progress, resultaten |
| DataStore | ✅ Werkt | Instellingen, premium flag |
| Response Time | ⚠️ Gedeeltelijk | Gemeten maar nog niet optimaal gebruikt |
| Geluid | ❌ Placeholder | Toggle UI werkt, geen audio |
| Premium Flow | ❌ Placeholder | Lokale flag, geen billing |
| UI Schermen | ✅ Werkt | Alle schermen functioneel |

## Oefentypes Status

| Type | Generator | Validator | UI | Opmerkingen |
|------|-----------|-----------|-----|-------------|
| VISUAL_QUANTITY | ✅ | ✅ | ✅ | Getalbeelden correct |
| VISUAL_GROUPS | ✅ | ✅ | ✅ | Splitsingen/groepjes |
| SIMPLE_SEQUENCE | ✅ | ✅ | ✅ | Skip counting |
| COMPARE_NUMBERS | ✅ | ✅ | ✅ | Vergelijken |
| TYPED_NUMERIC | ✅ | ✅ | ✅ | Optellen/aftrekken |
| MISSING_NUMBER | ✅ | ✅ | ✅ | Ontbrekend getal |
| NUMBER_LINE_CLICK | ⚠️ | ✅ | ⚠️ | Nummerlijn placeholder |

## Vaardigheden (18 skills)

### Gratis (6)
- foundation_number_images_5
- foundation_splits_10  
- arithmetic_add_10
- arithmetic_sub_10
- patterns_doubles
- patterns_halves

### Premium (12)
- foundation_splits_20
- arithmetic_add_20, arithmetic_sub_20
- arithmetic_bridge_add, arithmetic_bridge_sub
- patterns_count_2, patterns_count_5, patterns_count_10
- advanced_compare_100, advanced_place_value
- advanced_groups
- advanced_table_2, advanced_table_5, advanced_table_10

## Architectuur

```
app/src/main/java/com/rekenrinkel/
├── data/
│   ├── datastore/       # DataStore
│   ├── local/           # Room
│   └── repository/
├── domain/
│   ├── content/         # Skill configuraties
│   ├── engine/          # ExerciseEngine, SessionEngine
│   └── model/
├── ui/
│   ├── screens/
│   └── viewmodel/
```

## Vereisten

- Android Studio Hedgehog+
- JDK 17
- Android SDK 34
- `ANDROID_HOME` of `local.properties`:
```
sdk.dir=/path/to/android/sdk
```

## Testen

```bash
./gradlew test
```

Tests:
- ContentRepositoryTest
- ExerciseEngineTest
- ExerciseValidatorTest

## Changelog Iteratie 2

### Toegevoegd
- Gradle wrapper
- Plugin repositories configuratie

### Gedeeltelijk
- SessionEngine (basis werkt, verfijning nodig)
- Response time tracking (gemeten, niet optimaal gebruikt)

### Placeholder
- Geluidseffecten
- Premium billing

## Roadmap

### Iteratie 3 (gepland)
- [ ] SessionEngine verfijnen
- [ ] Response time integratie
- [ ] Didactische regels versterken

### V2 (toekomst)
- [ ] Billing
- [ ] Meer thema's
- [ ] Geluidseffecten

## Licentie

Copyright 2024 - Alle rechten voorbehouden