package com.vextech.editpro.core.classlibrary

/** The EditPro site's addresses (src/lib/site.ts), used for sharing and for the pieces still done on the website. */
object ClassLibraryLinks {
    const val ORIGIN = "https://editpro.site"

    /** Where reports about a Library document go, the same address the site uses. */
    const val CONTACT_EMAIL = "info@editpro.site"

    /** The Library page of the site. */
    const val LIBRARY_URL = "$ORIGIN/class-library"

    /** The page of one file on the site, which is what Share sends: whoever opens it sees the file and can download it. */
    fun fileUrl(fileId: String): String = "$ORIGIN/class-library/file?id=$fileId"
}
