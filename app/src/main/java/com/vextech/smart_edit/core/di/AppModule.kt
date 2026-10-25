package com.vextech.smart_edit.core.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.analytics.FirebaseAnalytics
import com.vextech.smart_edit.core.database.SmartEditDatabase
import com.vextech.smart_edit.core.pdf.PdfBoxEngine
import com.vextech.smart_edit.core.pdf.PdfEngine
import com.vextech.smart_edit.feature.converter.data.AndroidConverterRepository
import com.vextech.smart_edit.feature.converter.data.ConverterRepository
import com.vextech.smart_edit.feature.files.data.AndroidFilesRepository
import com.vextech.smart_edit.feature.files.data.FilesRepository
import com.vextech.smart_edit.feature.pdf.data.AndroidPdfEditorRepository
import com.vextech.smart_edit.feature.pdf.data.PdfEditorRepository
import com.vextech.smart_edit.feature.scanner.data.AndroidScannerRepository
import com.vextech.smart_edit.feature.scanner.data.ScannerRepository
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
        Room.databaseBuilder(context, SmartEditDatabase::class.java, "smart_edit.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    @Provides @Singleton fun analytics(@ApplicationContext context: Context): FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
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
