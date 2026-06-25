package com.aurorascan.di

import com.aurorascan.data.repository.AnnotationRepository
import com.aurorascan.data.repository.AnnotationRepositoryImpl
import com.aurorascan.data.repository.DocumentRepository
import com.aurorascan.data.repository.DocumentRepositoryImpl
import com.aurorascan.data.repository.SignatureRepository
import com.aurorascan.data.repository.SignatureRepositoryImpl
import com.aurorascan.engine.ocr.MlKitOcrEngine
import com.aurorascan.engine.ocr.OcrEngine
import com.aurorascan.engine.pdf.AndroidPdfEngine
import com.aurorascan.engine.pdf.PdfEngine
import com.aurorascan.engine.scan.DocumentScanEngine
import com.aurorascan.engine.scan.MlKitScanEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds engine + repository interfaces to their first-release implementations.
 * Swapping an engine later (CameraX, PaddleOCR, FastAPI sync) is a one-line
 * change here (blueprint section 8).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindDocumentScanEngine(impl: MlKitScanEngine): DocumentScanEngine

    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: MlKitOcrEngine): OcrEngine

    @Binds
    @Singleton
    abstract fun bindPdfEngine(impl: AndroidPdfEngine): PdfEngine

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindSignatureRepository(impl: SignatureRepositoryImpl): SignatureRepository

    @Binds
    @Singleton
    abstract fun bindAnnotationRepository(impl: AnnotationRepositoryImpl): AnnotationRepository
}
