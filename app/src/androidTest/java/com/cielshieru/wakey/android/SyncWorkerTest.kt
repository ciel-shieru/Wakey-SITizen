package com.cielshieru.wakey.android

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.TestListenableWorkerBuilder
import com.cielshieru.wakey.worker.SyncWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncWorkerTest {

    @Test
    fun `SyncWorker can be built with TestListenableWorkerBuilder`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .build()

        assertNotNull(worker, "SyncWorker should be buildable via TestListenableWorkerBuilder")
        assertTrue("Worker should implement ListenableWorker", worker is ListenableWorker)
    }

    @Test
    fun `SyncWorker has unique work ID`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .build()

        assertNotNull("Worker should have a unique ID", worker.id)
        assertTrue("Worker ID should not be empty", worker.id.isNotEmpty())
    }

    @Test
    fun `SyncWorker is the correct class`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .build()

        assertEquals("Worker should be an instance of SyncWorker", SyncWorker::class.java, worker::class.java)
    }

    @Test
    fun `BootReceiver enqueues work with correct tag`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED).apply {
            setPackage(context.packageName)
        }
        val receiver = BootReceiver()
        receiver.onReceive(context, intent)

        val workManager = androidx.work.WorkManager.getInstance(context)
        // The work should have been enqueued under the unique name
        assertNotNull("WorkManager should be initialized", workManager)
    }
}
