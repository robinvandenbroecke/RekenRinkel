PATCH 5-7 VERIFICATION: Idempotentie, DONE before advance, tests als contract

PATCH 5: Dubbele acties idempotent
- submitAnswer: guards op currentlyCompleting, completed, DONE, completionStage
- skipExercise: zelfde guards + ander-item guard
- continueWorkedExample: zelfde guards
- Eerste geldige call gaat door, latere calls genegeerd

PATCH 6: DONE vóór advance hard
- finishCurrentExercise: completed.add() in STAGE 5
- Pas daarna advanceToNextExercise()
- Advance checkt: exerciseId matcht, completionStage == DONE

PATCH 7: Tests als contract
- Geen asserts geschrapt
- Geen contract verzwakt
- Productiecode voldoet aan bestaande testcontracten

ALLE CONTRACTEN:
✓ worked example: exact één result, geen progress/rewards
✓ skip: exact één result [skipped], geen progress/rewards
✓ NOT_STARTED recovery: logt result, advance zonder duplicatie
✓ Latere recovery: geen dubbele logging/progress/rewards
✓ Double submit: genegeerd
✓ No double side effects: slaagt
✓ DONE before advance: strict ordering
