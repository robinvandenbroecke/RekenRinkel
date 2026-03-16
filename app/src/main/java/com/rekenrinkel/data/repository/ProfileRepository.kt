package com.rekenrinkel.data.repository

import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.local.dao.ProfileDao
import com.rekenrinkel.data.local.entity.ProfileEntity
import com.rekenrinkel.domain.model.Profile
import com.rekenrinkel.domain.model.Rewards
import com.rekenrinkel.domain.model.StartingBand
import com.rekenrinkel.domain.model.Theme
import com.rekenrinkel.domain.model.Badge
import com.rekenrinkel.domain.model.PlacementAnalysisResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.*

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val settingsDataStore: SettingsDataStore
) {
    fun getProfile(): Flow<Profile?> {
        return profileDao.getProfile().map { entity ->
            entity?.toProfile()
        }
    }

    suspend fun getProfileSync(): Profile? {
        return profileDao.getProfileSync()?.toProfile()
    }

    suspend fun createProfile(name: String, age: Int, theme: Theme = Theme.DINOSAURS): Profile {
        val startingBand = determineStartingBand(age)
        val profile = Profile(
            name = name,
            age = age,
            theme = theme,
            rewards = Rewards(),
            placementCompleted = false,
            startingBand = startingBand
        )
        profileDao.insertProfile(profile.toEntity())
        settingsDataStore.setProfileName(name)
        settingsDataStore.setTheme(theme)
        return profile
    }

    /**
     * PATCH 2: Maak profiel met leeftijdsbepaalde startconfiguratie
     * Geen placement verplicht - leeftijd bepaalt start
     */
    suspend fun createProfileWithStartConfig(
        name: String,
        age: Int,
        theme: Theme,
        startingBand: StartingBand,
        startSkills: List<String>,
        startCpaPhase: com.rekenrinkel.domain.content.CpaPhase
    ): Profile {
        val profile = Profile(
            name = name,
            age = age,
            theme = theme,
            rewards = Rewards(),
            placementCompleted = true,  // PATCH 1: Geen verplichte placement
            startingBand = startingBand,
            placementAnalysisResult = PlacementAnalysisResult(
                recommendedBand = startingBand,
                startSkills = startSkills,
                startCpaPhase = startCpaPhase,
                difficultyOffset = when (age) {
                    5, 6 -> 0
                    7, 8 -> 1
                    else -> 2
                }
            )
        )
        profileDao.insertProfile(profile.toEntity())
        settingsDataStore.setProfileName(name)
        settingsDataStore.setTheme(theme)
        return profile
    }

    suspend fun updateProfile(profile: Profile) {
        profileDao.updateProfile(profile.toEntity())
    }

    suspend fun completePlacement(profileId: String) {
        val current = profileDao.getProfileSync()?.toProfile() ?: return
        val updated = current.copy(placementCompleted = true)
        profileDao.updateProfile(updated.toEntity())
    }

    suspend fun clearAll() {
        profileDao.clearAll()
    }

    // ============ PLACEMENT METHODS ============

    fun determineStartingBand(age: Int): StartingBand {
        return when (age) {
            in 5..6 -> StartingBand.FOUNDATION
            in 7..8 -> StartingBand.EARLY_ARITHMETIC
            else -> StartingBand.EXTENDED
        }
    }

    fun getPlacementSkills(band: StartingBand): List<String> {
        return when (band) {
            StartingBand.FOUNDATION -> listOf(
                "foundation_subitize_5",
                "foundation_counting",
                "foundation_number_bonds_5",
                "foundation_number_bonds_10",
                "foundation_more_less"
            )
            StartingBand.EARLY_ARITHMETIC -> listOf(
                "foundation_number_bonds_10",
                "arithmetic_add_10",
                "arithmetic_sub_10",
                "arithmetic_add_20",
                "arithmetic_sub_20",
                "arithmetic_bridge_add"
            )
            StartingBand.EXTENDED -> listOf(
                "patterns_count_2",
                "advanced_groups",
                "advanced_table_2",
                "advanced_table_5",
                "advanced_place_value",
                "advanced_compare_100"
            )
        }
    }

    // ============ REWARDS METHODS ============

    suspend fun getRewards(): Rewards {
        return getProfileSync()?.rewards ?: Rewards()
    }

    suspend fun updateRewards(rewards: Rewards) {
        val current = getProfileSync() ?: return
        val updated = current.copy(rewards = rewards)
        profileDao.updateProfile(updated.toEntity())
    }

    suspend fun addXp(amount: Int): Rewards {
        val current = getProfileSync() ?: return Rewards()
        val updated = current.addXp(amount)
        profileDao.updateProfile(updated.toEntity())
        return updated.rewards
    }

    suspend fun updateStreak(): Rewards {
        val current = getProfileSync() ?: return Rewards()
        val updated = current.updateStreak()
        profileDao.updateProfile(updated.toEntity())
        return updated.rewards
    }

    suspend fun addBadge(badge: Badge) {
        val current = getProfileSync() ?: return
        val updated = current.copy(rewards = current.rewards.addBadge(badge))
        profileDao.updateProfile(updated.toEntity())
    }

    // ============ MAPPERS ============

    private fun ProfileEntity.toProfile(): Profile {
        return Profile(
            id = id,
            name = name,
            age = age,
            theme = Theme.valueOf(theme),
            createdAt = System.currentTimeMillis(),
            rewards = Rewards(
                totalXp = totalXp,
                currentLevel = currentLevel,
                currentStreak = currentStreak,
                longestStreak = longestStreak,
                lastSessionDate = lastSessionDate
            ),
            placementCompleted = placementCompleted,
            startingBand = StartingBand.valueOf(startingBand),
            // PATCH 2: Herstel placementAnalysisResult uit opgeslagen velden
            placementAnalysisResult = if (startSkills != null && startCpaPhase != null) {
                PlacementAnalysisResult(
                    recommendedBand = StartingBand.valueOf(startingBand),
                    startSkills = startSkills.split(","),
                    startCpaPhase = com.rekenrinkel.domain.content.CpaPhase.valueOf(startCpaPhase),
                    difficultyOffset = difficultyOffset
                )
            } else null
        )
    }

    private fun Profile.toEntity(): ProfileEntity {
        return ProfileEntity(
            id = id,
            name = name,
            age = age,
            theme = theme.name,
            currentLevel = rewards.currentLevel,
            totalXp = rewards.totalXp,
            currentStreak = rewards.currentStreak,
            longestStreak = rewards.longestStreak,
            lastSessionDate = rewards.lastSessionDate,
            placementCompleted = placementCompleted,
            startingBand = startingBand.name,
            // PATCH 2: Sla placement analyse op
            startSkills = placementAnalysisResult?.startSkills?.joinToString(","),
            startCpaPhase = placementAnalysisResult?.startCpaPhase?.name,
            difficultyOffset = placementAnalysisResult?.difficultyOffset ?: 0
        )
    }
}
