package com.rekenrinkel.data.repository

import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.local.dao.ProfileDao
import com.rekenrinkel.data.local.entity.ProfileEntity
import com.rekenrinkel.domain.model.Profile
import com.rekenrinkel.domain.model.Rewards
import com.rekenrinkel.domain.model.StartingBand
import com.rekenrinkel.domain.model.Theme
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
            currentLevel = 1,
            totalXp = 0,
            currentStreak = 0,
            longestStreak = 0,
            lastSessionDate = null,
            placementCompleted = false,
            startingBand = startingBand
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
        val current = profileDao.getProfileSync() ?: return
        val updated = current.copy(placementCompleted = true)
        profileDao.updateProfile(updated)
    }

    suspend fun clearAll() {
        profileDao.clearAll()
    }

    // ============ PLACEMENT METHODS ============

    /**
     * Bepaal startband op basis van leeftijd
     * Dit is de initiale inschatting, placement test verfijnt dit
     */
    fun determineStartingBand(age: Int): StartingBand {
        return when (age) {
            in 5..6 -> StartingBand.FOUNDATION
            in 7..8 -> StartingBand.EARLY_ARITHMETIC
            else -> StartingBand.EXTENDED
        }
    }

    /**
     * Genereer placement items op basis van startband
     * 6-10 items om het werkelijke niveau te bepalen
     */
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

    /**
     * Haal huidige rewards op (van profile)
     */
    suspend fun getRewards(): Rewards {
        val profile = profileDao.getProfileSync()
            ?: return Rewards()

        return Rewards(
            totalXp = profile.totalXp,
            currentLevel = profile.currentLevel,
            currentStreak = profile.currentStreak,
            longestStreak = profile.longestStreak,
            lastSessionDate = profile.lastSessionDate
        )
    }

    /**
     * Update rewards
     */
    suspend fun updateRewards(rewards: Rewards) {
        val current = profileDao.getProfileSync() ?: return

        val updated = current.copy(
            totalXp = rewards.totalXp,
            currentLevel = rewards.currentLevel,
            currentStreak = rewards.currentStreak,
            longestStreak = rewards.longestStreak,
            lastSessionDate = rewards.lastSessionDate
        )
        profileDao.updateProfile(updated)
    }

    /**
     * Voeg XP toe en update level
     */
    suspend fun addXp(amount: Int): Rewards {
        val current = getRewards()
        val newRewards = current.addXp(amount)
        updateRewards(newRewards)
        return newRewards
    }

    /**
     * Update daily streak
     */
    suspend fun updateStreak(): Rewards {
        val current = getRewards()
        val newRewards = current.updateStreak()
        updateRewards(newRewards)
        return newRewards
    }

    // ============ MAPPERS ============

    private fun ProfileEntity.toProfile(): Profile {
        return Profile(
            id = id,
            name = name,
            age = age,
            theme = Theme.valueOf(theme),
            createdAt = System.currentTimeMillis(),
            currentLevel = currentLevel,
            totalXp = totalXp,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastSessionDate = lastSessionDate,
            placementCompleted = placementCompleted,
            startingBand = StartingBand.valueOf(startingBand)
        )
    }

    private fun Profile.toEntity(): ProfileEntity {
        return ProfileEntity(
            id = id,
            name = name,
            age = age,
            theme = theme.name,
            currentLevel = currentLevel,
            totalXp = totalXp,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastSessionDate = lastSessionDate,
            placementCompleted = placementCompleted,
            startingBand = startingBand.name
        )
    }
}
