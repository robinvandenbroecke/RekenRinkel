package com.rekenrinkel.data.local.dao

import com.rekenrinkel.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeProfileDao : ProfileDao {
    private var profile: ProfileEntity? = null

    override fun getProfile(): Flow<ProfileEntity?> = flow { emit(profile) }

    override suspend fun getProfileSync(): ProfileEntity? = profile

    override suspend fun insertProfile(profile: ProfileEntity) {
        this.profile = profile
    }

    override suspend fun updateProfile(profile: ProfileEntity) {
        this.profile = profile
    }

    override suspend fun clearAll() {
        profile = null
    }
}
