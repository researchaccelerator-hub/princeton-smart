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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Registration writes credentials via [EncryptedCredentialsStore.storeCredentials];
 * [CloudAuthentication.autoSignIn] and [AuthRecoveryManager.attemptSilentReauth] read them back
 * via [EncryptedCredentialsStore.getStoredCredentials]. Both must resolve to the same encrypted
 * file and the same cached keyset, or credentials silently vanish and every concurrent first
 * access risks corrupting the underlying Tink keyset (surfacing as
 * `InvalidProtocolBufferException: Protocol message contained an invalid tag (zero)`). These
 * tests stub the static AndroidX factory methods so they run as plain JVM unit tests, without
 * touching the real Android Keystore.
 *
 * Devices whose keyset was corrupted before the creation lock existed still fail to decrypt, so
 * the store must also recover by deleting and recreating the file instead of throwing.
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
        every { fakeEditor.remove(any()) } answers {
            fakeBackingMap.remove(firstArg<String>())
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
    fun `a later login replaces the credentials saved at registration`() {
        val store = EncryptedCredentialsStore()

        store.storeCredentials(fakeContext(), "participant@example.com", "registered-password")
        store.storeCredentials(fakeContext(), "participant@example.com", "reset-password")

        assertEquals("reset-password", store.getStoredCredentials(fakeContext()).password)
    }

    @Test
    fun `cleared credentials read back as missing`() {
        val store = EncryptedCredentialsStore()
        store.storeCredentials(fakeContext(), "participant@example.com", "hunter2")

        store.clearCredentials(fakeContext())
        val creds = store.getStoredCredentials(fakeContext())

        assertEquals("", creds.email)
        assertEquals("", creds.password)
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

    @Test
    fun `a keyset that cannot be parsed is deleted and the store is recreated`() {
        val context = fakeContext()
        every {
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any())
        } throws corruptKeysetError() andThen fakePreferences

        val store = EncryptedCredentialsStore()
        store.storeCredentials(context, "a@b.com", "pw")
        val creds = store.getStoredCredentials(context)

        verify(exactly = 1) { context.get()!!.deleteSharedPreferences("encrypted_prefs") }
        assertEquals("a@b.com", creds.email)
        assertEquals("pw", creds.password)
    }

    @Test
    fun `credentials that cannot be decrypted are cleared and reported as missing`() {
        val context = fakeContext()
        val unreadablePreferences = mockk<SharedPreferences>()
        every { unreadablePreferences.getString(any(), any()) } throws SecurityException("Could not decrypt value")
        every {
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any())
        } returns unreadablePreferences andThen fakePreferences

        val store = EncryptedCredentialsStore()
        val creds = store.getStoredCredentials(context)

        verify(exactly = 1) { context.get()!!.deleteSharedPreferences("encrypted_prefs") }
        assertEquals("", creds.email)
        assertEquals("", creds.password)

        // The recreated store is cached and usable for the next registration.
        store.storeCredentials(context, "a@b.com", "pw")
        assertEquals("a@b.com", store.getStoredCredentials(context).email)
    }

    @Test
    fun `a failed write recreates the store and retries once`() {
        val context = fakeContext()
        val unwritablePreferences = mockk<SharedPreferences>()
        every { unwritablePreferences.edit() } throws SecurityException("Could not encrypt value")
        every {
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any())
        } returns unwritablePreferences andThen fakePreferences

        val store = EncryptedCredentialsStore()
        store.storeCredentials(context, "a@b.com", "pw")

        verify(exactly = 1) { context.get()!!.deleteSharedPreferences("encrypted_prefs") }
        assertEquals("a@b.com", fakeBackingMap["email"])
        assertEquals("pw", fakeBackingMap["password"])
    }

    @Test
    fun `storage that cannot be recreated yields empty credentials instead of crashing`() {
        every {
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any())
        } throws corruptKeysetError()

        val store = EncryptedCredentialsStore()
        store.storeCredentials(fakeContext(), "a@b.com", "pw")
        val creds = store.getStoredCredentials(fakeContext())

        assertNotNull(creds)
        assertEquals("", creds.email)
        assertEquals("", creds.password)
    }

    @Test
    fun `a failure to delete the storage yields empty credentials instead of crashing`() {
        val context = fakeContext()
        every { context.get()!!.deleteSharedPreferences(any()) } throws SecurityException("denied")
        every {
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any())
        } throws corruptKeysetError()

        val store = EncryptedCredentialsStore()
        store.storeCredentials(context, "a@b.com", "pw")
        val creds = store.getStoredCredentials(context)

        assertEquals("", creds.email)
        assertEquals("", creds.password)
    }

    @Test
    fun `a delayed read failure does not delete credentials saved by a concurrent recovery`() {
        val context = fakeContext()
        every { context.get()!!.deleteSharedPreferences("encrypted_prefs") } answers {
            fakeBackingMap.clear()
            true
        }
        val readerEntered = CountDownLatch(1)
        val writerFinished = CountDownLatch(1)
        val stalePreferences = mockk<SharedPreferences>()
        every { stalePreferences.getString(any(), any()) } answers {
            readerEntered.countDown()
            // Without the lock, the writer recovers the store and saves here, and this failure
            // then resets the store again. With the lock, the writer waits for this read.
            writerFinished.await(500, TimeUnit.MILLISECONDS)
            throw SecurityException("Could not decrypt value")
        }
        every { stalePreferences.edit() } throws SecurityException("Could not encrypt value")
        every {
            EncryptedSharedPreferences.create(any(), any(), any(), any(), any())
        } returns stalePreferences andThen fakePreferences

        val store = EncryptedCredentialsStore()
        val reader = thread { store.getStoredCredentials(context) }
        readerEntered.await(5, TimeUnit.SECONDS)
        val writer = thread {
            store.storeCredentials(context, "a@b.com", "pw")
            writerFinished.countDown()
        }
        reader.join(5_000)
        writer.join(5_000)

        assertEquals("a@b.com", fakeBackingMap["email"])
        assertEquals("pw", fakeBackingMap["password"])
    }

    private fun corruptKeysetError() =
        java.io.IOException("Protocol message contained an invalid tag (zero).")
}
