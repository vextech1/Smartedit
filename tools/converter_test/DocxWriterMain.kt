package com.vextech.smart_edit.feature.converter.data

import java.io.File
import java.util.zip.ZipFile

fun main() {
    val out = File.createTempFile("smartedit-converter", ".docx")
    try {
        SimpleDocxWriter.write("Hello & <world>\nSecond line", out)
        check(out.length() > 0)
        ZipFile(out).use { zip ->
            check(zip.getEntry("[Content_Types].xml") != null)
            check(zip.getEntry("_rels/.rels") != null)
            val doc = zip.getInputStream(zip.getEntry("word/document.xml")).bufferedReader().readText()
            check("Hello &amp; &lt;world&gt;" in doc)
            check("Second line" in doc)
        }
        println("Converter DOCX writer checks: PASS")
    } finally {
        out.delete()
    }
}
