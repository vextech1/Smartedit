package com.vextech.smart_edit.feature.pdf

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.PageRange
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

internal class PdfFilePrintAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build(),
            false,
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onWriteCancelled()
            return
        }
        runCatching {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
            }
        }.onSuccess {
            if (cancellationSignal.isCanceled) callback.onWriteCancelled()
            else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        }.onFailure { callback.onWriteFailed(it.message) }
    }
}

internal fun printPdf(context: Context, file: File) {
    require(file.isFile && file.length() > 0) { "Printable PDF is missing" }
    val manager = context.getSystemService(PrintManager::class.java)
    manager.print(file.nameWithoutExtension, PdfFilePrintAdapter(file), null)
}
