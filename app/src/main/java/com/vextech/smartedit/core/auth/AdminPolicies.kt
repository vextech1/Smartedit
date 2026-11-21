package com.vextech.smartedit.core.auth

/**
 * Who may manage things, worked out the same way as on the EditPro site.
 *
 * The owner's address is fixed in the Firestore rules (isOwner). The owner adds helpers; each helper is one document in the
 * `libraryAdmins` collection whose id is their lower-case email. The rules let a signed-in account with a verified email act as an admin
 * when such a document exists for it. `admins` is the older collection the site still reads, so it is read and cleaned up too.
 */
object AdminPolicies {
    const val ADMINS_COLLECTION = "libraryAdmins"
    const val LEGACY_ADMINS_COLLECTION = "admins"

    private val emailPattern = Regex("""^[^\s@/\\]+@[^\s@/\\]+\.[^\s@/\\]+$""")

    /** The form an email is stored and compared in: trimmed and lower-case. Google sign-in gives lower-case addresses. */
    fun normalizeEmail(email: String): String = email.trim().lowercase()

    /** Whether this looks like a usable email address. A sanity check for typing mistakes, not a deliverability test. */
    fun isValidEmail(email: String): Boolean {
        val e = normalizeEmail(email)
        return e.length <= 254 && emailPattern.matches(e)
    }

    /** The stored ids as a clean list: normalised, without repeats, in alphabetical order. */
    fun sortedAdmins(ids: List<String>): List<String> =
        ids.map(::normalizeEmail).filter { it.isNotEmpty() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
}

/** What a signed-in account can do. NONE is an ordinary user. */
enum class AdminRole {
    NONE,

    /** A helper the owner added: can manage, cannot see or change the helper list. */
    ADMIN,

    /** The owner named in the Firestore rules: can also manage the helper list. */
    OWNER,
    ;

    val isAdmin: Boolean get() = this != NONE
}
