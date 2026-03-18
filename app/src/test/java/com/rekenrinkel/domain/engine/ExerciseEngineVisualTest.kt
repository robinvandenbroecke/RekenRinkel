package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.*
import io.mockk.*
import org.junit.*
import org.junit.Assert.*

/**
 * PATCH 6: Regressietests voor visuele oefeningen
 * 
 * Controleert:
 * 1. foundation_subitize_5 genereert VISUAL_QUANTITY met geldige visualData
 * 2. Visuele oefeningen vallen niet terug op fallback
 * 3. VisualType.DOTS en BLOCKS worden correct gegenereerd
 * 4. Ontbrekende visualData wordt gedetecteerd
 */
class ExerciseEngineVisualTest {

    private lateinit var engine: ExerciseEngine

    @Before
    fun setup() {
        engine = ExerciseEngine()
    }

    @Test
    fun `foundation_subitize_5 generates VISUAL_QUANTITY with valid visualData`() {
        val exercise = engine.generateExercise("foundation_subitize_5", 1)
        
        // Moet VISUAL_QUANTITY zijn
        assertEquals(ExerciseType.VISUAL_QUANTITY, exercise.type)
        
        // visualData mag niet null zijn
        assertNotNull("visualData should not be null", exercise.visualData)
        
        // count moet 1-5 zijn
        val count = exercise.visualData?.count
        assertNotNull("count should not be null", count)
        assertTrue("count should be 1-5", count in 1..5)
        
        // correctAnswer moet overeenkomen met count
        assertEquals(count.toString(), exercise.correctAnswer)
    }

    @Test
    fun `foundation_number_images_5 generates VISUAL_QUANTITY with valid visualData`() {
        val exercise = engine.generateExercise("foundation_number_images_5", 1)
        
        assertEquals(ExerciseType.VISUAL_QUANTITY, exercise.type)
        assertNotNull("visualData should not be null", exercise.visualData)
        assertNotNull("count should not be null", exercise.visualData?.count)
    }

    @Test
    fun `foundation_counting generates VISUAL_QUANTITY with valid visualData`() {
        val exercise = engine.generateExercise("foundation_counting", 1)
        
        assertEquals(ExerciseType.VISUAL_QUANTITY, exercise.type)
        assertNotNull("visualData should not be null", exercise.visualData)
        assertNotNull("count should not be null", exercise.visualData?.count)
    }

    @Test
    fun `visual exercises do not fallback to generic TYPED_NUMERIC`() {
        val visualSkills = listOf(
            "foundation_subitize_5",
            "foundation_number_images_5", 
            "foundation_counting"
        )
        
        visualSkills.forEach { skillId ->
            val exercise = engine.generateExercise(skillId, 1)
            
            // Mag NIET TYPED_NUMERIC zijn
            assertNotEquals(
                "Skill $skillId should not fallback to TYPED_NUMERIC",
                ExerciseType.TYPED_NUMERIC, 
                exercise.type
            )
            
            // Moet VISUAL_QUANTITY zijn
            assertEquals(
                "Skill $skillId should generate VISUAL_QUANTITY",
                ExerciseType.VISUAL_QUANTITY,
                exercise.type
            )
        }
    }

    @Test
    fun `difficulty 1 generates VisualType DOTS`() {
        val exercise = engine.generateExercise("foundation_subitize_5", 1)
        
        assertEquals(VisualType.DOTS, exercise.visualData?.type)
    }

    @Test
    fun `difficulty 3 generates VisualType BLOCKS`() {
        val exercise = engine.generateExercise("foundation_subitize_5", 3)
        
        assertEquals(VisualType.BLOCKS, exercise.visualData?.type)
    }

    @Test
    fun `visualData contains type and count for all visual exercises`() {
        // Genereer meerdere oefeningen om variatie te testen
        repeat(10) {
            val exercise = engine.generateExercise("foundation_subitize_5", 2)
            
            assertNotNull("visualData null at iteration $it", exercise.visualData)
            assertNotNull("type null at iteration $it", exercise.visualData?.type)
            assertNotNull("count null at iteration $it", exercise.visualData?.count)
            
            // Type moet DOTS of BLOCKS zijn
            val type = exercise.visualData?.type
            assertTrue(
                "type should be DOTS or BLOCKS, was $type",
                type == VisualType.DOTS || type == VisualType.BLOCKS
            )
        }
    }

    @Test
    fun `unknown skill falls back to TYPED_NUMERIC`() {
        // Dit test dat onbekende skills WEL fallback gebruiken
        val exercise = engine.generateExercise("unknown_skill_xyz", 1)
        
        assertEquals(ExerciseType.TYPED_NUMERIC, exercise.type)
        assertNull(exercise.visualData)
    }
}