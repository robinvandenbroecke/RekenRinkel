package com.rekenrinkel.data.repository

import com.rekenrinkel.data.datastore.SettingsDataStore
import com.rekenrinkel.data.local.dao.ProfileDao
import com.rekenrinkel.data.local.entity.ProfileEntity
import com.rekenrinkel.domain.model.Badge
import com.rekenrinkel.domain.model.Profile
import com.rekenrinkel.domain.model.Rewards
import com.rekenrinkel.domain.model.Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.*

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val settingsDataStore: SettingsDataStore
) {
    fun getProfile(): Flow<Profile?> {
        return profileDao.getProfile().map { entity ->
            entity?.let {
                Profile(
                    id = it.id,
                    name = it.name,
                    theme = Theme.valueOf(it.theme),
                    currentLevel = it.currentLevel,
                    totalXp = it.totalXp,
                    currentStreak = it.currentStreak,
                    lastSessionDate = it.lastSessionDate,
                    longestStreak = it.longestStreak
                )
            }
        }
    }
    
    suspend fun createProfile(name: String, theme: Theme = Theme.DINOSAURS): Profile {
        val profile = Profile(
            name = name,
            theme = theme,
            currentLevel = 1,
            totalXp = 0,
            currentStreak = 0,
            lastSessionDate = null,
            longestStreak = 0
        )
        profileDao.insertProfile(profile.toEntity())
        settingsDataStore.setProfileName(name)
        settingsDataStore.setTheme(theme)
        return profile
    }
    
    suspend fun updateProfile(profile: Profile) {
        profileDao.updateProfile(profile.toEntity())
    }
    
    suspend fun addXp(xp: Int) {
        val current = profileDao.getProfileSync() ?: return
        val newTotalXp = current.totalXp + xp
        val newLevel = (newTotalXp / 100) + 1
        
        val updated = current.copy(
            totalXp = newTotalXp,
            currentLevel = newLevel
        )
        profileDao.updateProfile(updated)
    }
    
    suspend fun updateStreak() {
        val current = profileDao.getProfileSync() ?: return
        val today = Calendar.getInstance().apply { 
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        val lastSession = current.lastSessionDate ?: 0
        val yesterday = today - 24 * 60 * 60 * 1000
        
        val newStreak = when {
            lastSession >= today -> current.currentStreak
            lastSession >= yesterday -> current.currentStreak + 1
            else -> 1
        }
        
        val updated = current.copy(
            currentStreak = newStreak,
            lastSessionDate = System.currentTimeMillis(),
            longestStreak = maxOf(current.longestStreak, newStreak)
        )
        profileDao.updateProfile(updated)
    }
    
    private fun Profile.toEntity() = ProfileEntity(
        id = id,
        name = name,
        theme = theme.name,
        currentLevel = currentLevel,
        totalXp = totalXp,
        currentStreak = currentStreak,
        lastSessionDate = lastSessionDate,
        longestStreak = longestStreak
    )

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
            // Badges worden opgeslagen in SettingsDataStore of aparte tabel
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
}