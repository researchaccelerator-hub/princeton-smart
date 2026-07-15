package com.screenlake.recorder.viewmodels

import com.screenlake.data.database.entity.UserEntity
import com.screenlake.data.repository.GeneralOperationsRepository
import com.screenlake.data.repository.UserRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * insertUser() is the single place a new UserEntity row is created, whether the user just
 * logged in or just registered -- reconcilePendingReauthUser() is called here (rather than at
 * each Fragment's call site) specifically so a new insertion path can't accidentally bypass the
 * same-user/different-user data-protection check added for AC-1043.
 */
class UserViewModelTest {

    @Test
    fun `insertUser reconciles the pending reauth user before inserting the new user row`() = runTest {
        val userRepository = mockk<UserRepository>(relaxed = true)
        val generalOperationsRepository = mockk<GeneralOperationsRepository>(relaxed = true)
        val viewModel = UserViewModel(userRepository, generalOperationsRepository)
        val user = UserEntity(email = "participant@example.com")

        viewModel.insertUser(user)

        coVerifyOrder {
            generalOperationsRepository.reconcilePendingReauthUser(user)
            userRepository.insertUser(user)
        }
    }

    @Test
    fun `insertUser still inserts the user even when there is nothing to reconcile`() = runTest {
        val userRepository = mockk<UserRepository>(relaxed = true)
        val generalOperationsRepository = mockk<GeneralOperationsRepository>(relaxed = true)
        coEvery { generalOperationsRepository.reconcilePendingReauthUser(any()) } returns Unit
        val viewModel = UserViewModel(userRepository, generalOperationsRepository)
        val user = UserEntity(email = "participant@example.com")

        viewModel.insertUser(user)

        coVerify { userRepository.insertUser(user) }
    }
}
