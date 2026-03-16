package com.rekenrinkel.data.repository

import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.local.dao.ProfileDao
import com.rekenrinkel.data.local.entity.ProfileEntity
import com.rekenrinkel.domain.model.Profile
import com.rekenrinkel.domain.model.Rewards
import com.rekenrinkel.domain.model.StartingBand
import com.rekenrinkel.domain.model.Theme
import com.rekenrinkel.domain.model.Badge
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
            startingBand = StartingBand.valueOf(startingBand)
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
            startingBand = startingBand.name
        )
    }
}
