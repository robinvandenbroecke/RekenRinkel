package com.rekenrinkel.domain.engine

import com.rekenrinkel.domain.model.SkillProgress
import kotlinx.coroutines.flow.Flow

/**
 * Interface voor progress repository - gebruikt door SessionEngine
 * Maakt testen mogelijk zonder afhankelijkheid van concrete implementatie
 */
interface ProgressRepositoryInterface {
    fun getAllProgress(): Flow<List<SkillProgress>>
    fun getProgress(skillId: String): Flow<SkillProgress?>
}
