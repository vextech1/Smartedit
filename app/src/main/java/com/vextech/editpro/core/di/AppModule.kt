package com.vextech.editpro.core.di

import android.content.Context
import androidx.room.Room
import com.vextech.editpro.core.analytics.AnalyticsService
import com.vextech.editpro.core.database.EditProDatabase
import com.vextech.editpro.core.pdf.PdfBoxEngine
import com.vextech.editpro.core.pdf.PdfEngine
import com.vextech.editpro.feature.converter.data.AndroidConverterRepository
import com.vextech.editpro.feature.converter.data.ConverterRepository
import com.vextech.editpro.feature.files.data.AndroidFilesRepository
import com.vextech.editpro.feature.files.data.FilesRepository
import com.vextech.editpro.feature.pdf.data.AndroidPdfEditorRepository
import com.vextech.editpro.feature.pdf.data.PdfEditorRepository
import com.vextech.editpro.feature.scanner.data.AndroidScannerRepository
import com.vextech.editpro.feature.scanner.data.ScannerRepository
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
    @Provides @Singleton fun database(@ApplicationContext context: Context): EditProDatabase =
        Room.databaseBuilder(context, EditProDatabase::class.java, "editpro.db")
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
