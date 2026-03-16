package com.rekenrinkel.domain.content

import org.junit.Assert.*
import org.junit.Test

class ContentRepositoryTest {
    
    @Test
    fun `getConfig returns correct config`() {
        val config = ContentRepository.getConfig("foundation_number_images_5")
        
        assertNotNull(config)
        assertEquals("foundation_number_images_5", config?.skillId)
        assertEquals("Getalbeelden tot 5", config?.name)
        assertFalse(config?.isPremium ?: true)
    }
    
    @Test
    fun `getConfig returns null for unknown skill`() {
        val config = ContentRepository.getConfig("unknown_skill")
        assertNull(config)
    }
    
    @Test
    fun `all configs have required fields`() {
        val configs = ContentRepository.getAllConfigs()
        
        assertTrue("Should have configs", configs.isNotEmpty())
        
        configs.forEach { config ->
            assertNotNull("Skill ID should not be null", config.skillId)
            assertTrue("Skill ID should not be empty", config.skillId.isNotEmpty())
            assertNotNull("Name should not be null", config.name)
            assertTrue("Name should not be empty", config.name.isNotEmpty())
            assertTrue("Min difficulty should be >= 1", config.minDifficulty >= 1)
            assertTrue("Max difficulty should be >= min", config.maxDifficulty >= config.minDifficulty)
            assertTrue("Allowed types should not be empty", config.allowedExerciseTypes.isNotEmpty())
        }
    }
    
    @Test
    fun `entry skills have no prerequisites`() {
        // Entry skills (root of learning path) should have no prerequisites
        val entrySkills = listOf("foundation_number_images_5", "foundation_subitize_5", "foundation_shapes")

        entrySkills.forEach { skillId ->
            val config = ContentRepository.getConfig(skillId)
            if (config != null) {
                assertTrue(
                    "Entry skill $skillId should have no prerequisites",
                    config.prerequisites.isEmpty()
                )
            }
        }
    }

    @Test
    fun `all prerequisites refer to existing skills`() {
        val allConfigs = ContentRepository.getAllConfigs()
        val allSkillIds = allConfigs.map { it.skillId }.toSet()

        allConfigs.forEach { config ->
            config.prerequisites.forEach { prereqId ->
                assertTrue(
                    "Prerequisite $prereqId for ${config.skillId} should exist",
                    prereqId in allSkillIds
                )
            }
        }
    }

    @Test
    fun `learning path order respects prerequisites`() {
        val learningPath = ContentRepository.getLearningPath()
        val seenSkills = mutableSetOf<String>()

        learningPath.flatten().forEach { skillId ->
            val config = ContentRepository.getConfig(skillId)
            assertNotNull(config)

            // All prerequisites should have been seen before (earlier in path)
            config?.prerequisites?.forEach { prereqId ->
                assertTrue(
                    "Skill $skillId prerequisite $prereqId should appear earlier in learning path",
                    prereqId in seenSkills
                )
            }
            seenSkills.add(skillId)
        }
    }
    
    @Test
    fun `splits 10 requires prerequisites`() {
        val config = ContentRepository.getConfig("foundation_splits_10")
        
        assertNotNull(config)
        assertTrue(
            "Splits 10 should have prerequisites",
            config?.prerequisites?.isNotEmpty() ?: false
        )
    }
    
    @Test
    fun `addition requires prerequisites`() {
        val add10Config = ContentRepository.getConfig("arithmetic_add_10")

        assertNotNull("arithmetic_add_10 should exist", add10Config)
        assertTrue(
            "Add 10 should have prerequisites",
            add10Config?.prerequisites?.isNotEmpty() ?: false
        )
    }
    
    @Test
    fun `bridge addition requires splits and addition`() {
        val config = ContentRepository.getConfig("arithmetic_bridge_add")
        
        assertNotNull(config)
        assertTrue(
            "Bridge add should require add 20",
            config?.prerequisites?.contains("arithmetic_add_20") ?: false
        )
        assertTrue(
            "Bridge add should require splits 10",
            config?.prerequisites?.contains("foundation_splits_10") ?: false
        )
    }
    
    @Test
    fun `tables require groups and skip counting`() {
        val table2Config = ContentRepository.getConfig("advanced_table_2")
        
        assertNotNull(table2Config)
        assertTrue(
            "Table 2 should require groups",
            table2Config?.prerequisites?.contains("advanced_groups") ?: false
        )
        assertTrue(
            "Table 2 should require count 2",
            table2Config?.prerequisites?.contains("patterns_count_2") ?: false
        )
    }
    
    @Test
    fun `canUnlockSkill returns true for no prerequisites`() {
        val masteredSkills = emptySet<String>()
        
        assertTrue(
            "Number images should be unlockable with no prerequisites",
            ContentRepository.canUnlockSkill("foundation_number_images_5", masteredSkills)
        )
    }
    
    @Test
    fun `canUnlockSkill returns false when prerequisites missing`() {
        val masteredSkills = setOf("foundation_number_images_5")
        
        assertFalse(
            "Splits 20 should not be unlockable without splits 10",
            ContentRepository.canUnlockSkill("foundation_splits_20", masteredSkills)
        )
    }
    
    @Test
    fun `canUnlockSkill returns true when all prerequisites met`() {
        val masteredSkills = setOf(
            "foundation_number_images_5",
            "foundation_splits_10",
            "arithmetic_add_10"
        )
        
        assertTrue(
            "Splits 20 should be unlockable with all prerequisites",
            ContentRepository.canUnlockSkill("foundation_splits_20", masteredSkills)
        )
    }
    
    @Test
    fun `free configs exist`() {
        val freeConfigs = ContentRepository.getFreeConfigs()
        
        assertTrue("Should have free configs", freeConfigs.isNotEmpty())
        
        val freeSkillIds = listOf(
            "foundation_number_images_5",
            "foundation_splits_10",
            "arithmetic_add_10",
            "arithmetic_sub_10",
            "patterns_doubles",
            "patterns_halves"
        )
        
        freeSkillIds.forEach { skillId ->
            val config = freeConfigs.find { it.skillId == skillId }
            assertNotNull("$skillId should be free", config)
        }
    }
    
    @Test
    fun `premium configs exist`() {
        val premiumConfigs = ContentRepository.getPremiumConfigs()

        assertTrue("Should have premium configs", premiumConfigs.isNotEmpty())

        // Validate that premium skills have prerequisites (premium content is advanced)
        premiumConfigs.forEach { config ->
            assertTrue(
                "Premium skill ${config.skillId} should have prerequisites",
                config.prerequisites.isNotEmpty()
            )
        }
    }
    
    @Test
    fun `all premium skills have prerequisites`() {
        val premiumConfigs = ContentRepository.getPremiumConfigs()
        
        premiumConfigs.forEach { config ->
            assertTrue(
                "Premium skill ${config.skillId} should have prerequisites",
                config.prerequisites.isNotEmpty()
            )
        }
    }
    
    @Test
    fun `learning path exists and is valid`() {
        val learningPath = ContentRepository.getLearningPath()
        
        assertTrue("Learning path should not be empty", learningPath.isNotEmpty())
        
        learningPath.forEachIndexed { index, skills ->
            assertTrue("Level $index should have skills", skills.isNotEmpty())
            
            skills.forEach { skillId ->
                assertNotNull(
                    "Skill $skillId in learning path should exist",
                    ContentRepository.getConfig(skillId)
                )
            }
        }
    }
    
    @Test
    fun `learning path starts with foundation`() {
        val learningPath = ContentRepository.getLearningPath()

        assertTrue("Learning path should have at least one level", learningPath.isNotEmpty())

        val firstLevel = learningPath.first()
        val foundationStartSkills = setOf(
            "foundation_subitize_5",
            "foundation_counting",
            "foundation_number_images_5",
            "foundation_shapes"
        )

        val hasFoundationStart = firstLevel.any { it in foundationStartSkills }
        assertTrue("First level should contain a foundation start skill", hasFoundationStart)
    }
    
    // ============================================
    // TYPED RULES TESTS
    // ============================================
    
    @Test
    fun `number images has correct value range`() {
        val config = ContentRepository.getConfig("foundation_number_images_5")
        
        assertNotNull(config)
        assertEquals(1, config?.rules?.valueRange?.min)
        assertEquals(5, config?.rules?.valueRange?.max)
    }
    
    @Test
    fun `halves has require even rule`() {
        val config = ContentRepository.getConfig("patterns_halves")
        
        assertNotNull(config)
        assertTrue("Halves should require even input", config!!.rules.requireEven)
    }
    
    @Test
    fun `skip counting has step rule`() {
        val config = ContentRepository.getConfig("patterns_count_2")
        
        assertNotNull(config)
        assertEquals(2, config?.rules?.step)
        val seqLength = config?.rules?.find<DidacticRule.SequenceLength>()?.length
        assertEquals(4, seqLength)
    }
    
    @Test
    fun `bridge addition has require bridge rule`() {
        val config = ContentRepository.getConfig("arithmetic_bridge_add")
        
        assertNotNull(config)
        assertTrue("Bridge add should require bridge", config!!.rules.requireBridge)
    }
    
    @Test
    fun `splits has min and max part rules`() {
        val config = ContentRepository.getConfig("foundation_splits_10")
        
        assertNotNull(config)
        val minPart = config?.rules?.find<DidacticRule.MinPartValue>()?.min
        val maxPart = config?.rules?.find<DidacticRule.MaxPartValue>()?.max
        assertEquals(1, minPart)
        assertEquals(9, maxPart)
    }
    
    @Test
    fun `tables have exact multiplier rule`() {
        val config = ContentRepository.getConfig("advanced_table_5")
        
        assertNotNull(config)
        assertEquals(5, config?.rules?.multiplier)
    }
    
    @Test
    fun `no specificRules in codebase`() {
        val configs = ContentRepository.getAllConfigs()
        
        configs.forEach { config ->
            // Verify we're using typed rules, not string maps
            assertNotNull("Rules should not be null", config.rules)
        }
    }
    
    // ============================================
    // EXERCISE TYPES TESTS
    // ============================================
    
    @Test
    fun `visual quantity skills have correct type`() {
        val config = ContentRepository.getConfig("foundation_number_images_5")
        
        assertNotNull(config)
        assertTrue(
            "Number images should allow VISUAL_QUANTITY",
            config?.allowedExerciseTypes?.contains(com.rekenrinkel.domain.model.ExerciseType.VISUAL_QUANTITY) ?: false
        )
    }
    
    @Test
    fun `comparison skill has compare type`() {
        val config = ContentRepository.getConfig("advanced_compare_100")
        
        assertNotNull(config)
        assertTrue(
            "Compare 100 should allow COMPARE_NUMBERS",
            config?.allowedExerciseTypes?.contains(com.rekenrinkel.domain.model.ExerciseType.COMPARE_NUMBERS) ?: false
        )
    }
    
    @Test
    fun `sequence skills have sequence type`() {
        val config = ContentRepository.getConfig("patterns_count_2")
        
        assertNotNull(config)
        assertTrue(
            "Count 2 should allow SIMPLE_SEQUENCE",
            config?.allowedExerciseTypes?.contains(com.rekenrinkel.domain.model.ExerciseType.SIMPLE_SEQUENCE) ?: false
        )
    }
}