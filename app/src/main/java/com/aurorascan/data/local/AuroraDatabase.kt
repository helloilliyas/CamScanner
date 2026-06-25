package com.aurorascan.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.aurorascan.data.local.dao.AnnotationDao
import com.aurorascan.data.local.dao.AssetDao
import com.aurorascan.data.local.dao.DocumentDao
import com.aurorascan.data.local.dao.OcrDao
import com.aurorascan.data.local.dao.PageDao
import com.aurorascan.data.local.entity.AnnotationEntity
import com.aurorascan.data.local.entity.AssetEntity
import com.aurorascan.data.local.entity.DocumentEntity
import com.aurorascan.data.local.entity.OcrPageEntity
import com.aurorascan.data.local.entity.PageEntity

@Database(
    entities = [
        DocumentEntity::class,
        PageEntity::class,
        OcrPageEntity::class,
        AssetEntity::class,
        AnnotationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AuroraDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun ocrDao(): OcrDao
    abstract fun assetDao(): AssetDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        const val NAME = "aurorascan.db"
    }
}
