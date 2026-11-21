package com.vextech.smartedit.core.di

import android.content.Context
import androidx.room.Room
import com.vextech.smartedit.core.analytics.AnalyticsService
import com.vextech.smartedit.core.database.SmartEditDatabase
import com.vextech.smartedit.core.pdf.PdfBoxEngine
import com.vextech.smartedit.core.pdf.PdfEngine
import com.vextech.smartedit.feature.converter.data.AndroidConverterRepository
import com.vextech.smartedit.feature.converter.data.ConverterRepository
import com.vextech.smartedit.feature.files.data.AndroidFilesRepository
import com.vextech.smartedit.feature.files.data.FilesRepository
import com.vextech.smartedit.feature.pdf.data.AndroidPdfEditorRepository
import com.vextech.smartedit.feature.pdf.data.PdfEditorRepository
import com.vextech.smartedit.feature.scanner.data.AndroidScannerRepository
import com.vextech.smartedit.feature.scanner.data.ScannerRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppProvidesModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): SmartEditDatabase =
        Room.databaseBuilder(context, SmartEditDatabase::class.java, "smartedit.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    @Provides @Singleton fun analytics(@ApplicationContext context: Context): AnalyticsService = AnalyticsService(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindsModule {
    @Binds @Singleton abstract fun bindPdfEngine(impl: PdfBoxEngine): PdfEngine
    @Binds @Singleton abstract fun bindConverterRepository(impl: AndroidConverterRepository): ConverterRepository
    @Binds @Singleton abstract fun bindFilesRepository(impl: AndroidFilesRepository): FilesRepository
    @Binds @Singleton abstract fun bindPdfEditorRepository(impl: AndroidPdfEditorRepository): PdfEditorRepository
    @Binds @Singleton abstract fun bindScannerRepository(impl: AndroidScannerRepository): ScannerRepository
}
