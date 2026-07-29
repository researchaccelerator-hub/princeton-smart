package com.screenlake.recorder.authentication

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * Registration writes credentials via [EncryptedCredentialsStore.storeCredentials];
 * [CloudAuthentication.autoSignIn] and [AuthRecoveryManager.attemptSilentReauth] read them back
 * via [EncryptedCredentialsStore.getStoredCredentials]. Both must resolve to the same encrypted
 * file and the same cached keyset, or credentials silently vanish and every concurrent first
 * access risks corrupting the underlying Tink keyset (surfacing as
 * `InvalidProtocolBufferException: Protocol message contained an invalid tag (zero)`). These
 * tests stub the static AndroidX factory methods so they run as plain JVM unit tests, without
 * touching the real Android Keystore.
 */
class EncryptedCredentialsStoreTest {

    private val fakeBackingMap = mutableMapOf<String, String>()
    private lateinit var fakePreferences: SharedPreferences

    @Before
    fun setUp() {
        fakeBackingMap.clear()

        val fakeEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        every { fakeEditor.putString(any(), any()) } answers {
            fakeBackingMap[firstArg()] = secondArg()
            fakeEditor
        }

        fakePreferences = mockk()
        every { fakePreferences.getString(any(), any()) } answers {
            fakeBackingMap[firstArg()] ?: secondArg()
        }
        every { fakePreferences.edit() } returns fakeEditor

        mockkStatic(MasterKeys::class)
        mockkStatic(EncryptedSharedPreferences::class)
        every { MasterKeys.getOrCreate(any()) } returns "fake-alias"
        every {
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any())
        } returns fakePreferences
    }

    @After
    fun tearDown() {
        unmockkStatic(MasterKeys::class)
        unmockkStatic(EncryptedSharedPreferences::class)
    }

    private fun fakeContext(): WeakReference<Context> {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        return WeakReference(context)
    }

    @Test
    fun `credentials stored are the same credentials later retrieved`() {
        val store = EncryptedCredentialsStore()

        store.storeCredentials(fakeContext(), "participant@example.com", "hunter2")
        val creds = store.getStoredCredentials(fakeContext())

        assertEquals("participant@example.com", creds.email)
        assertEquals("hunter2", creds.password)
    }

    @Test
    fun `the underlying encrypted preferences file is created only once across multiple callers`() {
        val store = EncryptedCredentialsStore()

        // Simulates CloudAuthentication.autoSignIn, AuthRecoveryManager.attemptSilentReauth, and
        // registration's storeCredentials all reaching this store around the same time, each
        // with their own Context reference.
        store.getStoredCredentials(fakeContext())
        store.storeCredentials(fakeContext(), "a@b.com", "pw")
        store.getStoredCredentials(fakeContext())

        verify(exactly = 1) { EncryptedSharedPreferences.create(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `reads and writes use the same file name`() {
        val store = EncryptedCredentialsStore()
        val fileNameSlot = slot<String>()
        every {
            EncryptedSharedPreferences.create(capture(fileNameSlot), any(), any(), any(), any())
        } returns fakePreferences

        store.storeCredentials(fakeContext(), "a@b.com", "pw")

        assertEquals("encrypted_prefs", fileNameSlot.captured)
    }
}
