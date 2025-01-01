package com.screenlake.recorder.viewmodels

import androidx.lifecycle.ViewModel
import com.screenlake.data.repository.UserRepository
import com.screenlake.data.database.entity.UserEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UserViewModel @Inject constructor(
    private val userRepository: UserRepository
) : ViewModel() {

    suspend fun userExist() : Boolean {
        return userRepository.userExist()
    }

    suspend fun getUser() : UserEntity {
        return userRepository.getUser()
    }

    suspend fun insertUser(user: UserEntity) {
        userRepository.insertUser(user)
    }

    suspend fun deleteUser() {
        userRepository.deleteUser()
    }
}