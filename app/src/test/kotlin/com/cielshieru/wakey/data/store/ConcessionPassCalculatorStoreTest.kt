package com.cielshieru.wakey.data.store

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ConcessionPassCalculatorStoreTest {

    @Test
    fun `calculator costs persist independently and blank inputs are cleared`() = runTest {
        val testFile = File("/tmp/test_concession_costs_${System.nanoTime()}.preferences_pb")
        val datastore = PreferenceDataStoreFactory.create(produceFile = { testFile })

        datastore.saveConcessionPassCosts(
            toCampusCostInput = "2.45",
            fromCampusCostInput = "3.50"
        )
        assertEquals(
            ConcessionPassCosts("2.45", "3.50"),
            datastore.data.concessionPassCostsFlow().first()
        )

        datastore.saveConcessionPassCosts(
            toCampusCostInput = "",
            fromCampusCostInput = "4"
        )
        assertEquals(
            ConcessionPassCosts("", "4"),
            datastore.data.concessionPassCostsFlow().first()
        )

        testFile.delete()
    }
}
