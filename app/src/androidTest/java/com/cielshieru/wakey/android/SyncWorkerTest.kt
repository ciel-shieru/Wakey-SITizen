package com.cielshieru.wakey.android

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.test.assertNull

class SyncWorkerTest {

    @Test
    fun `SyncWorker can be built with TestListenableWorkerBuilder`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val worker = TestListenableWorkerBuilder<com.cielshieru.wakey.worker.SyncWorker>(context)
            .build()

        assertNotNull(worker, "SyncWorker should be buildable via TestListenableWorkerBuilder")
        assert(worker is ListenableWorker) { "Worker should implement ListenableWorker" }
    }

    @Test
    fun `SyncWorker has unique work name`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val worker = TestListenableWorkerBuilder<com.cielshieru.wakey.worker.SyncWorker>(context)
            .build()

        assertNotNull(worker.id) { "Worker should have a unique ID" }
    }
}
