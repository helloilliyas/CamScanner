package com.aurorascan.data.repository

import com.aurorascan.core.model.NormalizedRect
import com.aurorascan.engine.ocr.OcrBlock
import com.aurorascan.engine.ocr.OcrLine
import com.aurorascan.engine.ocr.OcrWord
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrSerializationTest {

    @Test
    fun `ocr blocks survive a json round trip`() {
        val blocks = listOf(
            OcrBlock(
                text = "Invoice 42",
                bounds = NormalizedRect(0.1f, 0.1f, 0.9f, 0.2f),
                lines = listOf(
                    OcrLine(
                        text = "Invoice 42",
                        bounds = NormalizedRect(0.1f, 0.1f, 0.9f, 0.2f),
                        words = listOf(
                            OcrWord("Invoice", NormalizedRect(0.1f, 0.1f, 0.5f, 0.2f), confidence = 0.97f),
                            OcrWord("42", NormalizedRect(0.55f, 0.1f, 0.9f, 0.2f), confidence = 0.91f),
                        ),
                    ),
                ),
            ),
        )

        val json = AuroraJson.encodeToString(blocks)
        val restored = AuroraJson.decodeFromString<List<OcrBlock>>(json)

        assertEquals(blocks, restored)
        assertEquals("42", restored.first().lines.first().words[1].text)
    }
}
