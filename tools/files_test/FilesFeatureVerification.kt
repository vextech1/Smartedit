import com.vextech.smart_edit.feature.files.data.ManagedFileOperator
import com.vextech.smart_edit.feature.files.domain.FileItem
import com.vextech.smart_edit.feature.files.domain.FileNamePolicy
import com.vextech.smart_edit.feature.files.domain.FileSort
import com.vextech.smart_edit.feature.files.domain.FileTreePolicy
import java.nio.file.Files

private fun assertTrue(value: Boolean, message: String) { if (!value) error(message) }
private fun assertEquals(expected: Any?, actual: Any?, message: String = "") { if (expected != actual) error("Expected <$expected> but was <$actual>. $message") }
private fun file(id:String,name:String,parent:String?=null,dir:Boolean=false,size:Long=0,modified:Long=0)=FileItem(id,name,"file:///$name",if(dir)"inode/directory" else "text/plain",size,0,modified,parent,dir,"managed",false)

fun main() {
    assertEquals("Report.pdf", FileNamePolicy.validateUserName(" Report.pdf "))
    listOf("", ".", "..", "a/b", "a\\b", "a..b", "bad:name", "line\nbreak").forEach { bad ->
        assertTrue(runCatching { FileNamePolicy.validateUserName(bad) }.isFailure, "Expected invalid name: $bad")
    }

    val rootFolder = file("a", "Alpha", null, true)
    val childFolder = file("b", "Beta", "a", true)
    val nested = file("c", "nested.txt", "b", false, 20, 3)
    val rootFile = file("d", "root.txt", null, false, 10, 2)
    val items = listOf(rootFolder, childFolder, nested, rootFile)
    assertEquals(listOf("", "Alpha", "Beta"), FileTreePolicy.breadcrumbs(items, "b").map { it.name })
    assertTrue(FileTreePolicy.isDescendant(items, "b", "a"), "Beta should be a descendant of Alpha")
    assertTrue("b" !in FileTreePolicy.moveTargets(items, "a").map { it.id }, "Cannot move a folder into its descendant")
    assertEquals(listOf("Alpha", "root.txt"), FileTreePolicy.visibleChildren(items, null, FileSort.NAME, true).map { it.name })

    val temp = Files.createTempDirectory("smartedit_files_test").toFile()
    try {
        val source = temp.resolve("source.txt").apply { writeText("hello") }
        val target = temp.resolve("folder").resolve("renamed.txt")
        ManagedFileOperator.relocate(source, target)
        assertTrue(!source.exists() && target.readText() == "hello", "Relocate must move the real file")
        ManagedFileOperator.rollback(target, source)
        assertTrue(source.exists() && !target.exists(), "Rollback must restore the real file")

        val existing = temp.resolve("existing.txt").apply { writeText("x") }
        assertTrue(runCatching { ManagedFileOperator.relocate(source, existing) }.isFailure, "Relocate must refuse collisions")
        assertTrue(source.exists() && existing.readText() == "x", "Collision must not damage either file")
    } finally {
        temp.deleteRecursively()
    }

    println("Files feature verification passed")
}
