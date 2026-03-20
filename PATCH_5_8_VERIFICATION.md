PATCH 5-8 VERIFICATION: UI en docs gecontroleerd

PATCH 5 - ExerciseScreen en MainActivity compatibel met Optie B:
- ExerciseScreen: stepState parameter voor ERROR detectie
- ExerciseScreen: onContinueAfterError callback
- ExerciseScreen: onContinueWorkedExample callback voor worked examples
- ExerciseScreen: ErrorOverlay component voor error weergave
- ExerciseScreen: enabled = !showFeedback blokkeert input tijdens feedback
- MainActivity: stepState = uiState.stepState.name doorgegeven
- MainActivity: onContinueAfterError = { viewModel.continueAfterError() }
- MainActivity: onContinueWorkedExample = { viewModel.continueWorkedExample() }
- GEEN UI-gestuurde timing of pseudo-state-machine

PATCH 6 - Testdependencies gecontroleerd:
- testImplementation("junit:junit:4.13.2")
- testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
- testImplementation("io.mockk:mockk:1.13.9")
- testImplementation("androidx.arch.core:core-testing:2.2.0")
- Alle testdependencies aanwezig in app/build.gradle.kts

PATCH 7 - Optie B systematisch hersteld:
- LessonStepState: SHOWING, PROCESSING, FEEDBACK, ADVANCING, ERROR, COMPLETED
- CompletionStage: NOT_STARTED → RESULT_LOGGED → PROGRESS_UPDATED → REWARDS_APPLIED → READY_TO_ADVANCE → DONE
- FailureStage: VALIDATION, RESULT_LOGGING, PROGRESS_UPDATE, REWARD_UPDATE, ADVANCE, UNKNOWN
- finishCurrentExercise() als centrale state machine
- CompletionMode: FEEDBACK_THEN_ADVANCE, DIRECT_CONTINUE, SKIP_ADVANCE
- LessonViewModelFlowTest.kt met contract tests

PATCH 8 - Statusdocs gecontroleerd:
- README.md: stage-based architectuur gedocumenteerd
- V1_STATUS.md: lessonflow states gedocumenteerd
- Geen marketingtaal, eerlijke status (privétestfase)

CI: GitHub Actions loopt op elke push naar main.
