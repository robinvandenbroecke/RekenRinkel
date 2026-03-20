PATCH VERIFICATION: Alle semantische paden correct

PATCH 1: Skip semantisch volledig apart
- SKIP_ADVANCE mode: log result, geen progress/rewards, direct advance
- Exact één result met [skipped]
- Tweede skip volledig genegeerd via guards

PATCH 2: Worked example semantisch volledig apart
- DIRECT_CONTINUE mode: log result, geen progress/rewards, direct advance
- Geen validator
- Geen feedback delay

PATCH 3: Gewone/guided answerflow exact contractueel
- FEEDBACK_THEN_ADVANCE mode:
  1. validation
  2. result log
  3. progress update
  4. rewards update
  5. feedback
  6. READY_TO_ADVANCE
  7. DONE
  8. completedExerciseIds.add()
  9. advance

PATCH 4: DONE hard vóór advance
- completedExerciseIds.add() in finishCurrentExercise()
- Pas daarna advanceToNextExercise()

PATCH 5: Recovery exact per stage
- NOT_STARTED: geen side effects, geen DONE/completed
- RESULT_LOGGED: geen dubbele logging
- PROGRESS_UPDATED: geen dubbele progress
- REWARDS_APPLIED: geen dubbele rewards
- READY_TO_ADVANCE: directe veilige advance
- DONE: geen side effects meer

PATCH 6: Idempotentie hard
- Guards in entrypoints: DONE, completed, currentlyCompleting
- finishCurrentExercise: stage guards, completed guards
- Recovery: completed guards, stage evaluatie

Status: Alle patches compleet, semantisch correct.
