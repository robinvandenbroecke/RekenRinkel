PATCH 3-7: Harde idempotentie, DONE semantiek, recovery en skip

PATCH 3: completedExerciseIds als harde eindafscherming
- Voor side effects: check isExerciseCompleted() → direct return
- Alleen toevoegen als item semantisch echt klaar is (na DONE, vóór advance)
- Recovery checkt completedExerciseIds → geen side effects meer

PATCH 4: DONE semantisch sterker
- DONE betekent: alle side effects afgerond
- Item mag niet meer opnieuw verwerkt worden
- Recovery mag geen result/progress/reward meer uitvoeren
- Harde guards in entrypoints en finishCurrentExercise

PATCH 5: finalize/advance éénrichtingsverkeer
- Sequence: READY_TO_ADVANCE → DONE → completedExerciseIds.add() → advance
- Geen pad waarbij guard losgelaten wordt terwijl item nog "in transit" is
- Pas na succesvolle overgang guard vrijgeven

PATCH 6: Recovery exact zonder dubbele side effects
- Evaluatie op: failureStage, completionStage, completedExerciseIds, exerciseType
- NOT_STARTED: log recovery result
- RESULT_LOGGED+: markeer als DONE, geen dubbele side effects
- DONE/completed: geen side effects meer

PATCH 7: Skip volledig hard idempotent
- Exact één result met [skipped]
- Geen validator
- Geen progress/rewards (SKIP_ADVANCE mode)
- Direct advance
- Tweede skip-call volledig genegeerd via guard + completed set

Doel: double submit, no double side effects, skip exact één result,
recovery logt niets dubbel - allemaal idempotent en veilig.
