PATCH 5-7 VERIFICATION: Recovery en foutpaden

PATCH 5: Recovery exact per stage
- NOT_STARTED: geen result loggen, alleen DONE markeren zonder side effects
- RESULT_LOGGED: geen dubbele logging, alleen DONE
- PROGRESS/REWARDS/READY: geen updates, alleen DONE
- DONE: geen side effects

PATCH 6: Skip en worked example foutpaden apart
- SKIP_ADVANCE mode: geen validator, geen progress/rewards
- DIRECT_CONTINUE mode: geen validator, geen progress/rewards
- Normale/guided: normale stage-based recovery

PATCH 7: Tests als contract
- Geen asserts geschrapt
- Geen contract verzwakt
- Code voldoet aan test contracten

Alle guards correct:
- currentlyCompletingExerciseId: GEEN reset in catches
- completedExerciseIds: alleen voor definitief afgesloten items
- DONE: semantisch volledig afgerond
