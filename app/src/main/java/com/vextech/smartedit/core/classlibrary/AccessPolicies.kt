package com.vextech.smartedit.core.classlibrary

/**
 * Who may download what, the same rule as the EditPro site (access.ts):
 *  - everyone's first download is free, with no account;
 *  - after that a person signs in with Google and uploads a study document, and each upload unlocks [DOWNLOADS_PER_UPLOAD] more downloads;
 *  - only Library downloads are counted; every other tool stays free.
 * This is the logic only. The numbers it works from are read and written by [LibraryAccessRepository].
 */
object AccessPolicies {
    const val FREE_DOWNLOADS = 1
    const val DOWNLOADS_PER_UPLOAD = 3

    enum class Access { OWN, FREE, UNLOCKED, CREDIT, SIGN_IN, UPLOAD }

    data class Input(
        val fileId: String,
        /** The id of the file this device already used its free download on, or null. */
        val free: String?,
        val own: Boolean = false,
        val signedIn: Boolean,
        val unlocked: Boolean,
        val liveUploads: Int,
        val used: Int,
    )

    data class Decision(val access: Access, val left: Int)

    /** How many more downloads an account has: each live upload gives [DOWNLOADS_PER_UPLOAD], each download takes one. Never below 0. */
    fun creditsLeft(liveUploads: Int, used: Int): Int = maxOf(0, DOWNLOADS_PER_UPLOAD * maxOf(0, liveUploads) - maxOf(0, used))

    /** What happens when someone asks for a file. [Access.FREE] and [Access.CREDIT] mean "allowed", and the caller must record it. */
    fun decide(i: Input): Decision {
        val left = if (i.signedIn) creditsLeft(i.liveUploads, i.used) else 0
        return when {
            i.own -> Decision(Access.OWN, left)
            i.free == i.fileId -> Decision(Access.FREE, left)
            i.signedIn && i.unlocked -> Decision(Access.UNLOCKED, left)
            i.free == null -> Decision(Access.FREE, left)
            !i.signedIn -> Decision(Access.SIGN_IN, left)
            left > 0 -> Decision(Access.CREDIT, left)
            else -> Decision(Access.UPLOAD, left)
        }
    }

    fun isAllowed(access: Access): Boolean = access == Access.OWN || access == Access.FREE || access == Access.UNLOCKED || access == Access.CREDIT
}
