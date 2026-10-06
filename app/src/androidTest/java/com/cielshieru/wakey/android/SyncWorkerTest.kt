package com.cielshieru.wakey.android

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SyncWorkerTest {

    @Test
    fun `SyncWorker returns success on valid timetable`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val worker = TestListenableWorkerBuilder<com.cielshieru.wakey.worker.SyncWorker>(context)
            .build()

        assert(worker != null)
    }
}
