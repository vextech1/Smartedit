import com.vextech.smart_edit.feature.pdf.data.WorkingCopyHistory
import java.nio.file.Files
import java.io.File

fun main() {
    val root = Files.createTempDirectory("smartedit-pdf-history").toFile()
    try {
        val working = File(root, "working.pdf").apply { writeText("v1") }
        val history = WorkingCopyHistory(File(root, "history"), 2)
        history.checkpoint(working); working.writeText("v2")
        history.checkpoint(working); working.writeText("v3")
        check(history.canUndo)
        check(history.undo(working) && working.readText() == "v2")
        check(history.undo(working) && working.readText() == "v1")
        check(history.redo(working) && working.readText() == "v2")
        history.checkpoint(working); working.writeText("v4")
        check(!history.canRedo)
        history.markSaved()
        check(!history.canUndo && !history.canRedo)
        println("PDF_HISTORY_TESTS_PASS")
    } finally { root.deleteRecursively() }
}
