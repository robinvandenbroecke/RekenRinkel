# RekenRinkel

Educatieve rekenapp voor kinderen van ongeveer 6 jaar.

**Status**: Technische basis aanwezig, nog niet productierijp  
**Versie**: 1.0 (Testbuild voor privégebruik)

## Build

### Vereisten
- Android Studio Hedgehog+ (of nieuwer)
- JDK 17
- `ANDROID_HOME` omgevingsvariabele, OF `local.properties` bestand met `sdk.dir=/pad/naar/android/sdk`

### Lokale Debug Build

```bash
# Debug APK (voor testen)
./gradlew assembleDebug
```

**Output locatie**: `app/build/outputs/apk/debug/app-debug.apk`

### CI Build (GitHub Actions)

De repository bevat een GitHub Actions workflow die automatisch een debug APK bouwt bij elke push naar `main` of `develop`.

**Workflow bestand**: `.github/workflows/build-debug-apk.yml`

**Hoe te gebruiken**:
1. Push naar GitHub: `git push origin main`
2. Ga naar **Actions** tab in GitHub repository
3. Selecteer de meest recente workflow run
4. Download `debug-apk` artifact

**Artifact bewaarduur**: 30 dagen

### Release Build

```bash
# Release APK (vereist signing configuratie)
./gradlew assembleRelease
```

**Output locatie**: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Build Varianten

| Variant | Gebruik | APK Locatie | CI |
|---------|---------|-------------|-----|
| `debug` | Dagelijks testen | `app/build/outputs/apk/debug/app-debug.apk` | ✅ Ja |
| `release` | Pre-release test | `app/build/outputs/apk/release/` | ❌ Nee |

## Installatie (Sideload)

### OnePlus 12 (Android 14)
1. Zet APK over via USB: `adb install app-debug.apk`
2. Of gebruik "Installatie van onbekende bronnen" in Instellingen
3. App verschijnt in app drawer als "RekenRinkel"

### BOOX Note Air 4C (Android 13)
1. Zet APK over via USB of cloud storage
2. Accepteer "onbekende bronnen" tijdens installatie
3. **Belangrijk**: Schakel "E-Ink Modus" in via Instellingen voor optimale weergave
4. App is geoptimaliseerd voor e-ink schermen (minimale animaties, hoog contrast)

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
| UI Schermen | Functioneel aanwezig | Grote touch targets, responsive layout |
| E-Ink Modus | Aanwezig | Minimale animaties, hoog contrast |
| Tests | Gedeeltelijk | Unit tests, geen integratietests |
| Geluid | Placeholder | UI toggle, geen audio |
| Premium | Placeholder | Lokale flag, geen billing |

## E-Ink Modus

Voor BOOX Note Air 4C en andere e-ink apparaten:

- **Inschakelen**: Instellingen → E-Ink Modus
- **Effect**: 
  - Minimale animaties (geen fade-ins/outs)
  - Hoog contrast (zwart/wit)
  - Geen kleurafhankelijkheid
  - Simpele, stabiele UI
  - Grote touch targets

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
- 100% offline, geen cloud

## Beperkingen

- Geen geluidseffecten
- Geen Play Store billing
- Geen integratietests
- Geen breed gebruikersgetest
- SessionEngine adaptiviteit niet gevalideerd
- Response time tracking niet gevalideerd in echte sessies

## Reset Opties

Via Instellingen:
- **Reset Voortgang**: Wis alle skill voortgang (sessies, oefeningen)
- **Reset Profiel**: Wis profiel + alle data (als nieuwe installatie)

## Licentie

Copyright 2024 - Privé testbuild

---

## Device Test Checklist

### OnePlus 12 (Telefoon)

#### Installatie
- [ ] APK installeert zonder fouten
- [ ] App start op
- [ ] Eerste keer opstarten toont onboarding

#### Layout & Touch
- [ ] Home scherm past op scherm
- [ ] Oefenscherm knoppen zijn groot genoeg (48dp+)
- [ ] Geen overlappende elementen
- [ ] Scroll werkt soepel

#### Functionaliteit
- [ ] Oefeningen genereren werkt
- [ ] Antwoorden invoeren werkt
- [ ] Resultaten worden opgeslagen
- [ ] Instellingen worden onthouden

#### Reset
- [ ] Reset voortgang werkt
- [ ] Reset profiel werkt

### BOOX Note Air 4C (E-ink Tablet)

#### Installatie
- [ ] APK installeert zonder fouten
- [ ] App start op
- [ ] E-ink modus inschakelen werkt

#### E-Ink Optimalisatie
- [ ] E-Ink modus is actief (minimale animaties)
- [ ] Hoge contrast weergave
- [ ] Geen "ghosting" van vorige schermen
- [ ] UI updates zijn stabiel

#### Layout & Touch
- [ ] Alle schermen passen op 10.3" scherm
- [ ] Touch targets zijn groot genoeg (64dp+ aanbevolen voor e-ink)
- [ ] Tekst is leesbaar
- [ ] Geen te fijne details

#### Functionaliteit
- [ ] Oefeningen werken correct
- [ ] Touch respons is acceptabel
- [ ] Geen verplichte snelle interacties

### Algemene Tests

#### Data
- [ ] Profiel blijft bewaard na herstart
- [ ] Voortgang blijft bewaard na herstart
- [ ] Instellingen blijven bewaard na herstart
- [ ] App herstart correct zonder errors

#### Offline
- [ ] App werkt zonder internet
- [ ] Geen crash bij ontbrekende connectie
- [ ] Geen cloud afhankelijkheden

#### Stabiliteit
- [ ] App crasht niet tijdens oefenen
- [ ] App crasht niet bij snel schakelen
- [ ] Back button werkt correct