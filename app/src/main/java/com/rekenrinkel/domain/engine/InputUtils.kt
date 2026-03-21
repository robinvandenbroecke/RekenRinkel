package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.ExerciseType

/**
 * Input modes for exercises - determines what UI to show.
 */
enum class InputMode {
    MULTIPLE_CHOICE,    // 4 option buttons in 2x2 grid
    NUMBER_PAD,         // Compact 3x4 numpad with auto-submit
    CONTINUE_BUTTON,    // Single "Ik snap het" / "Volgende" button
    COMPARISON          // Three buttons: <, =, >
}

/**
 * Determines the input mode based on age, exercise type, and difficulty.
 * This is the single source of truth for how a child answers an exercise.
 */
fun determineInputMode(age: Int, exerciseType: ExerciseType, difficulty: Int): InputMode {
    // Worked examples: always continue button
    if (exerciseType == ExerciseType.WORKED_EXAMPLE) return InputMode.CONTINUE_BUTTON

    // Compare numbers: always comparison buttons
    if (exerciseType == ExerciseType.COMPARE_NUMBERS) return InputMode.COMPARISON

    // Age 5-6: ALWAYS multiple choice
    if (age <= 6) return InputMode.MULTIPLE_CHOICE

    // Age 7+: MC for visual/comparison types
    return when (exerciseType) {
        ExerciseType.VISUAL_QUANTITY,
        ExerciseType.VISUAL_GROUPS,
        ExerciseType.MULTIPLE_CHOICE -> InputMode.MULTIPLE_CHOICE

        ExerciseType.GUIDED_PRACTICE,
        ExerciseType.TYPED_NUMERIC,
        ExerciseType.MISSING_NUMBER,
        ExerciseType.SIMPLE_SEQUENCE,
        ExerciseType.NUMBER_LINE_CLICK -> {
            // Easy difficulty → MC, harder → numpad
            if (difficulty <= 2) InputMode.MULTIPLE_CHOICE else InputMode.NUMBER_PAD
        }

        else -> InputMode.NUMBER_PAD
    }
}
