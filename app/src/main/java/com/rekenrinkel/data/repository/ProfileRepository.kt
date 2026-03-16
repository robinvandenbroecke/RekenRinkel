package com.rekenrinkel.data.repository

import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.local.dao.ProfileDao
import com.rekenrinkel.data.local.entity.ProfileEntity
import com.rekenrinkel.domain.model.Profile
import com.rekenrinkel.domain.model.Rewards
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

    suspend fun createProfile(name: String, theme: Theme = Theme.DINOSAURS): Profile {
        val profile = Profile(
            name = name,
            theme = theme,
            currentLevel = 1,
            totalXp = 0,
            currentStreak = 0,
            longestStreak = 0,
            lastSessionDate = null
        )
        profileDao.insertProfile(profile.toEntity())
        settingsDataStore.setProfileName(name)
        settingsDataStore.setTheme(theme)
        return profile
    }

    suspend fun updateProfile(profile: Profile) {
        profileDao.updateProfile(profile.toEntity())
    }

    suspend fun clearAll() {
        profileDao.clearAll()
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
            age = 6, // Default, kan uitgebreid worden in entity
            theme = Theme.valueOf(theme),
            createdAt = System.currentTimeMillis(), // Default, kan uitgebreid worden in entity
            currentLevel = currentLevel,
            totalXp = totalXp,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastSessionDate = lastSessionDate
        )
    }

    private fun Profile.toEntity(): ProfileEntity {
        return ProfileEntity(
            id = id,
            name = name,
            theme = theme.name,
            currentLevel = currentLevel,
            totalXp = totalXp,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            lastSessionDate = lastSessionDate
        )
    }
}
