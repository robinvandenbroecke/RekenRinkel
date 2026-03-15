package com.rekenrinkel.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.rekenrinkel.domain.model.Theme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "rekenrinkel_settings")

class SettingsDataStore(private val context: Context) {
    
    private object Keys {
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val THEME = stringPreferencesKey("theme")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val PREMIUM_UNLOCKED = booleanPreferencesKey("premium_unlocked")
        val PARENT_MODE_ENABLED = booleanPreferencesKey("parent_mode_enabled")
    }
    
    val profileName: Flow<String> = context.dataStore.data.map { 
        it[Keys.PROFILE_NAME] ?: "" 
    }
    
    val theme: Flow<Theme> = context.dataStore.data.map { 
        Theme.valueOf(it[Keys.THEME] ?: Theme.DINOSAURS.name) 
    }
    
    val soundEnabled: Flow<Boolean> = context.dataStore.data.map { 
        it[Keys.SOUND_ENABLED] != false 
    }
    
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { 
        it[Keys.ONBOARDING_COMPLETED] == true 
    }
    
    val premiumUnlocked: Flow<Boolean> = context.dataStore.data.map { 
        it[Keys.PREMIUM_UNLOCKED] == true 
    }
    
    val parentModeEnabled: Flow<Boolean> = context.dataStore.data.map { 
        it[Keys.PARENT_MODE_ENABLED] == true 
    }
    
    suspend fun setProfileName(name: String) {
        context.dataStore.edit { it[Keys.PROFILE_NAME] = name }
    }
    
    suspend fun setTheme(theme: Theme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }
    
    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }
    
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }
    
    suspend fun setPremiumUnlocked(unlocked: Boolean) {
        context.dataStore.edit { it[Keys.PREMIUM_UNLOCKED] = unlocked }
    }
    
    suspend fun setParentModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PARENT_MODE_ENABLED] = enabled }
    }
}