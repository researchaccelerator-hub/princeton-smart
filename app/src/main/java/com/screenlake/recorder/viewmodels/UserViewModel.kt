package com.screenlake.recorder.viewmodels

import androidx.lifecycle.ViewModel
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.data.repository.UserRepository
import com.screenlake.data.database.entity.UserEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val generalOperationsRepository: GeneralOperationsRepository
) : ViewModel() {

    suspend fun userExist() : Boolean {
        return userRepository.userExist()
    }

    suspend fun getUser() : UserEntity {
        return userRepository.getUser()
    }

    /**
     * Inserting a user here always represents either a fresh login or a fresh registration --
     * the two points where a different participant could be starting a session on this device.
     * Reconciling here (rather than at each call site) means every insertion path is covered by
     * construction, not by remembering to add the check wherever a new UserEntity is created.
     *
     * Passing the full [user] (rather than just its email) lets reconciliation restore the
     * invite code / tenant / panel fields onto it in place, before it's inserted, when the same
     * participant is reconnecting -- see GeneralOperationsRepository.reconcilePendingReauthUser.
     */
    suspend fun insertUser(user: UserEntity) {
        generalOperationsRepository.reconcilePendingReauthUser(user)
        userRepository.insertUser(user)
    }

    suspend fun deleteUser() {
        userRepository.deleteUser()
    }
}