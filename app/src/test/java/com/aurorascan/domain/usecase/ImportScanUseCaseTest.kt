package com.aurorascan.domain.usecase

import com.aurorascan.data.repository.DocumentRepository
import com.aurorascan.engine.scan.ScannedPageSource
import com.aurorascan.engine.scan.ScanResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportScanUseCaseTest {

    private val repository = mockk<DocumentRepository>()
    private val scheduler = mockk<com.aurorascan.work.OcrScheduler>()
    private val useCase = ImportScanUseCase(repository, scheduler)

    private val scan = ScanResult(
        pages = listOf(ScannedPageSource(mockk(relaxed = true))),
        generatedPdfUri = null,
    )

    @Test
    fun `imports scan and schedules ocr for the new document`() = runTest {
        val titleSlot = slot<String>()
        coEvery { repository.importScan(eqScan(), capture(titleSlot)) } returns "doc-1"
        every { scheduler.schedule(any(), any()) } just Runs

        val id = useCase(scan, title = null, languageHints = setOf("en"))

        assertEquals("doc-1", id)
        assertTrue("default title should mention Scan", titleSlot.captured.startsWith("Scan"))
        coVerify(exactly = 1) { scheduler.schedule("doc-1", setOf("en")) }
    }

    @Test
    fun `uses provided title when present`() = runTest {
        coEvery { repository.importScan(eqScan(), "Receipt") } returns "doc-2"
        every { scheduler.schedule(any(), any()) } just Runs

        val id = useCase(scan, title = "Receipt")

        assertEquals("doc-2", id)
        coVerify { scheduler.schedule("doc-2", any()) }
    }

    private fun eqScan(): ScanResult = scan
}
