# Accounts: Firebase setup

Smart Edit signs people in the same way the EditPro site does: **Google sign-in through Firebase Auth**, one login for everyone.
Ordinary users and admins use the same button; what an account may do is decided by the **Firestore rules**, not by the app.

Until the steps below are done, the app works exactly as before and the account screens say "Accounts are not available in this build yet."
No code change is needed to turn accounts on.

## What you set up in Firebase

1. **Use the same Firebase project as the site** (`editpro1`), so the app and the site share one set of rules, owner and admin list.
2. **Authentication → Sign-in method:** turn on **Google**.
3. **Project settings → Your apps → Add app → Android**
   - Package name: `com.vextech.smartedit`
   - Add the **SHA-1 and SHA-256** fingerprints of every key the app is signed with: the debug key, your release key, and the Play App Signing key (Play Console → App integrity). Sign-in fails with "no credentials" if a fingerprint is missing.
4. **Download `google-services.json`** and put it at `app/google-services.json`. Download it *after* Google sign-in is on and the fingerprints are added, so it contains the OAuth web client. The build already applies the Google services plugin whenever that file exists.
5. **Firestore rules:** the site's `firestore.rules` already cover everything the app does, so nothing new is needed. They name the **owner** by email (`isOwner()`) and let the owner add **helper admins** as documents in the `libraryAdmins` collection (document id = the helper's lower-case email). Deploy with `firebase deploy --only firestore:rules` if you change the owner.

## How it works in the app

| Piece | What it does |
| --- | --- |
| `core/auth/AuthRepository` | Google account chooser (Credential Manager), then `signInWithCredential`. Sign out. Delete account. Turns itself off when `google-services.json` is missing. |
| `core/auth/AdminRepository` | Asks the server what the account may do, using read-only questions the rules answer: the owner can read `libraryAdmins`; any admin can list not-yet-live `userFiles`. Also lists, adds and removes helper admins (owner only). |
| `core/auth/AdminPolicies` | Email cleaning and checks, the same rules as the site's `admins.ts`. Covered by `AdminPoliciesTest`. |
| **Profile → account card** | Ordinary user: Sign in with Google, Sign out, Delete my account. |
| **Settings → Admin** | Admin sign-in, shows whether the account is owner, admin or neither, and (owner only) the helper list. |

Three roles, same as the site: **user** (any Google account), **admin** (a helper in `libraryAdmins`), **owner** (the address fixed in the rules).
An account needs a verified email to be an admin; Google accounts have one.

## Checking it works

1. Install a build signed with a key whose SHA-1 you added. Open **Settings → Profile** and tap **Sign in with Google**.
2. Open **Settings → Admin** while signed in with a non-admin account: it should say "This account is not an admin".
3. Sign in with the owner address: it should say "You are the owner" and show **Helper admins**.
4. Add a second Google address, sign in with it, and open Admin: it should say "You are an admin" and not show the helper list.

## Things to know

- The admin check asks the server each time (never the phone's cache), so a removed helper loses access as soon as they reopen Admin.
- The admin screen manages the helper list only. The site's Library upload tools do not exist in the app, so there is nothing else to administer yet.
- "Delete my account" removes the account's `users/<uid>` records and the sign-in. If Google says the sign-in is too old, the person signs out, signs in again and retries.
- Only English strings were added; other languages show English until translated.
