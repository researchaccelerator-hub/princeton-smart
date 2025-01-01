package com.screenlake.data.repository

import com.screenlake.data.database.dao.UserDao
import com.screenlake.data.database.entity.UserEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {

    suspend fun userExist() = userDao.userExists()

    suspend fun getUser() = userDao.getUser()

    suspend fun insertUser(user: UserEntity) {
        userDao.insertUserObj(user)
    }

    suspend fun deleteUser() = userDao.deleteUser()
}