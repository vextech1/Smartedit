package com.vextech.editpro.core.classlibrary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserLibraryPoliciesTest {
    private val meta = UploadMeta("Algebra notes.pdf", null, "sec", "cls", null, LibraryCategory.NOTES, null)

    @Test fun studentUploadLimits() {
        assertEquals(Check.Ok, UserLibraryPolicies.checkUserUpload("a.pdf", 50_000))
        assertTrue(UserLibraryPolicies.checkUserUpload("a.pdf", 500) is Check.Refused)
        assertTrue(UserLibraryPolicies.checkUserUpload("a.pdf", 6L * 1024 * 1024) is Check.Refused)
        assertTrue(UserLibraryPolicies.checkUserUpload("a.zip", 50_000) is Check.Refused)
        assertTrue(UserLibraryPolicies.checkUserUpload("a#b.pdf", 50_000) is Check.Refused)
        assertTrue(UserLibraryPolicies.checkUserUpload(".hidden.pdf", 50_000) is Check.Refused)
    }

    @Test fun adminUploadAllowsZipAndLargerFiles() {
        assertEquals(Check.Ok, UserLibraryPolicies.checkAdminUpload("pack.zip", 10L * 1024 * 1024))
        assertTrue(UserLibraryPolicies.checkAdminUpload("pack.zip", 21L * 1024 * 1024) is Check.Refused)
    }

    @Test fun contentMustMatchExtension() {
        assertTrue(UserLibraryPolicies.looksLikeExt("pdf", "%PDF-1.7".toByteArray()))
        assertFalse(UserLibraryPolicies.looksLikeExt("pdf", "PK\u0003\u0004".toByteArray()))
        assertTrue(UserLibraryPolicies.looksLikeExt("docx", byteArrayOf(0x50, 0x4b, 3, 4)))
        assertFalse(UserLibraryPolicies.looksLikeExt("txt", byteArrayOf(65, 0, 66)))
    }

    @Test fun userEntryHasOnlyAllowedFields() {
        val e = UserLibraryPolicies.buildUserEntry("uid1", meta, 20_000, 1, null, "2026-01-02")
        assertEquals("uploading", e["status"])
        assertEquals("uid1", e["uploader"])
        assertEquals("pdf", e["ext"])
        assertFalse(e.containsKey("subject"))
        assertFalse(e.containsKey("label"))
        assertEquals("2026-01-02", e["added"])
    }

    @Test fun splitChunksCoversAllBytes() {
        val parts = UserLibraryPolicies.splitChunks(ByteArray(2500), 1000)
        assertEquals(listOf(1000, 1000, 500), parts.map { it.size })
    }

    @Test fun newIdsMatchSiteShape() {
        repeat(20) { assertTrue(Regex("^f[a-z0-9]{8,32}$").matches(UserLibraryPolicies.newFileId())) }
    }

    @Test fun chooseShardUsesRoomThenNewShard() {
        assertEquals(1, UserLibraryPolicies.chooseShard(listOf(599_900, 100), 500))
        assertEquals(2, UserLibraryPolicies.chooseShard(listOf(599_900, 599_900), 500))
        assertNull(UserLibraryPolicies.chooseShard(List(ClassLibraryParser.MAX_SHARDS) { 599_900 }, 500))
    }

    @Test fun reasonMustBeWritten() {
        assertFalse(UserLibraryPolicies.checkReason("no").first)
        assertEquals("Not study material", UserLibraryPolicies.checkReason("  Not   study material ").second)
        assertFalse(UserLibraryPolicies.checkReason("x".repeat(501)).first)
    }

    @Test fun libraryRoomRefusesWhenFull() {
        assertEquals(Check.Ok, UserLibraryPolicies.checkLibraryRoom(1000, 1000, 1, 1000))
        assertTrue(UserLibraryPolicies.checkLibraryRoom(UserLibraryPolicies.STORAGE_LIMIT_BYTES, 0, 0, 10) is Check.Refused)
    }

    @Test fun parseUserFileRejectsBadRecords() {
        val ok = mapOf<String, Any?>(
            "uploader" to "u", "name" to "a.pdf", "ext" to "pdf", "section" to "s", "cls" to "c", "category" to "notes",
            "size" to 20000L, "parts" to 1L, "status" to "live", "added" to "2026-01-02",
        )
        assertEquals("live", UserLibraryPolicies.parseUserFile("fabcdefgh12", ok)?.status)
        assertNull(UserLibraryPolicies.parseUserFile("bad id", ok))
        assertNull(UserLibraryPolicies.parseUserFile("fabcdefgh12", ok + ("ext" to "exe")))
        assertNull(UserLibraryPolicies.parseUserFile("fabcdefgh12", ok + ("status" to "weird")))
    }

    @Test fun studentFileAppearsAsCloudFile() {
        val f = UserLibraryPolicies.parseUserFile(
            "fabcdefgh12",
            mapOf("uploader" to "u", "name" to "a.pdf", "ext" to "pdf", "section" to "s", "cls" to "c", "category" to "notes", "size" to 20000L, "parts" to 1L, "status" to "live"),
        )!!
        val c = UserLibraryPolicies.toCloudFile(f)
        assertTrue(c.student)
        assertEquals("u", c.uploader)
    }
}
