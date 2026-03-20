PATCH 3-4 VERIFICATION: completedExerciseIds en advanceAfterError correct

PATCH 3: completedExerciseIds alleen voor definitieve afsluiting
- NOT_STARTED: GEEN completed
- RESULT_LOGGED: GEEN completed
- PROGRESS_UPDATED: GEEN completed
- REWARDS_APPLIED: GEEN completed
- READY_TO_ADVANCE: WEL completed (echte finalize)
- DONE: WEL completed (al klaar)

PATCH 4: advanceAfterError() dom en veilig
- Alleen overgang naar volgend item / les einde
- GEEN finalize-semantiek
- GEEN completed-set wijzigingen
- GEEN DONE-zetting
- EERST nieuw item in state, DAN guard reset

Alle patches compleet.
