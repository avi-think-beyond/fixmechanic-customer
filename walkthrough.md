# Security Summary

**Reviewed Files**: 17
**Issues Resolved**: 7

### Issues Resolved
| Area | File | Line | What Was Happening | Impact | Status | How It Was Fixed |
|---|---|---|---|---|---|---|
| Open Service Requests Exposure | `firestore.rules` | 100 | Any signed-in customer could read or list another customer's `serviceRequests` whenever the status was `REQUESTED` or `SEARCHING`, and could hijack unassigned requests. | High | Fixed | Removed open-status read/list bypasses so customers can only access their own requests (`customerId == request.auth.uid`), and restricted mechanic claiming/listing to verified mechanics (`isMechanicUser()`) and admins. |
| Unscoped Subcollections & Threads | `firestore.rules` | 150 | Request chat messages, responses, audit events, `/chats`, and `/notifications` allowed reads or writes by any signed-in account regardless of participation. | High | Fixed | Added `isRequestParticipant(requestId)` and `isChatParticipant(chatId)` checks enforcing participant ownership and `senderId == request.auth.uid`. |
| Persistent Disk Cache Leakage | `app/src/main/java/com/example/di/ServiceLocator.kt` | 40 | Default Firestore SQLite persistence kept User A's cached documents on disk after logout/account switch. | High | Fixed | Configured `FirebaseFirestoreSettings` with `MemoryCacheSettings` so zero user records persist to disk across sessions. |
| Asynchronous Logout & Listener Leaks | `app/src/main/java/com/example/data/repository/FirebaseAuthRepository.kt` | 42 | `logout()` relied on asynchronous `AuthStateListener` callbacks and did not clear chat listeners or guard in-flight snapshot callbacks. | High | Fixed | Wired synchronous `clearAllUserSessions()` into `logout()` and account switches, added `AtomicLong` `sessionGeneration` + `uid` verification in all Firestore repositories, and enforced `customerId == userId` filtering on parsed requests. |
| Chat & Profile Listener Leaks | `app/src/main/java/com/example/data/repository/FirestoreChatRepository.kt` | 35 | Chat `flowMap`/`listenerMap` stayed attached across account switches and `ChatViewModel.loadChat()` did not cancel prior collectors. | High | Fixed | Added `AuthStateListener` teardown, `clearSession()` removing all listeners and resetting flows, and `chatMessagesJob?.cancel()` in `ChatViewModel`. |
| Credential & ViewModel State Carryover | `app/src/main/java/com/example/ui/navigation/AppNavHost.kt` | 93 | Logout kept vehicle details, location inputs, photo URI, AI diagnosis, profile state, and Google Credential Manager session state intact and left `isVerified` true for an instant frame. | High | Fixed | Reset all ViewModels synchronously on logout, cleared `CredentialManager` state inside `NonCancellable`, popped the entire navigation stack (`popUpTo(0)`), and removed hardcoded PII defaults from `UserProfileData`. |
| Cross-App Rule Compatibility | `firestore.rules` | 36 | Customer-only rule edits previously blocked Mechanic Pro login, GPS updates, and request claiming on the shared database. | Medium | Fixed | Preserved Mechanic Pro & Admin permissions via `isMechanicUser()` / `isAdmin()` alongside strict customer-to-customer isolation. |

### Safeguards Verified
| Area | File | How It's Protected |
|---|---|---|
| Server-Derived Identity | `firestore.rules` | All ownership checks derive identity exclusively from `request.auth.uid` on verified Firebase ID tokens, never trusting client-supplied IDs without matching `request.auth.uid`. |
| Customer Profile Protection | `firestore.rules` | Unfiltered listing of `/users` is denied for customers; self-registration as `admin` is rejected. |
| Authentication & Token Refresh | `app/src/main/java/com/example/data/repository/FirebaseAuthRepository.kt` | Uses Google Sign-In via Jetpack Credential Manager and clears previous credential state on logout. |
| Client-Side Cache Isolation | `app/src/main/java/com/example/di/ServiceLocator.kt` | Memory-only Firestore cache (`MemoryCacheSettings`) ensures all cached documents vanish when cleared or on logout. |

## PoC Verification

### Cross-Account Service Request, Chat & Session Leakage

#### Summary
| Field | Value |
|---|---|
| Issue Type | Broken Object-Level Authorization & Client Session Carryover |
| Impact | High |
| Affected File | `firestore.rules:100`, `app/src/main/java/com/example/data/repository/FirestoreEmergencyRepository.kt:85`, `app/src/main/java/com/example/di/ServiceLocator.kt:40` |
| Test Scenario | User A signs in, creates a `REQUESTED` service request and chat message, logs out, and User B signs in and attempts to read User A's request, messages, and cached UI state. |

#### How It Was Fixed
Enforced strict `customerId == request.auth.uid` authorization on `serviceRequests` and `isRequestParticipant(requestId)` on subcollections in `firestore.rules` (while preserving Mechanic Pro access via `isMechanicUser()`), switched Firestore to memory-only caching (`MemoryCacheSettings`), added synchronous `clearSession()` + `sessionGeneration` token validation across all repositories, and reset all ViewModels, Credential Manager state, and the navigation backstack on logout.

#### Step-by-Step Trace
| Step | What Happens in the Code | Outcome |
|---|---|---|
| 1 | User A logs out via `MainScreen` -> `AppNavHost.kt:93` | `emergencyViewModel.resetForLogout()`, `chatViewModel.resetForLogout()`, `aiViewModel.clear()`, `profileViewModel.resetForLogout()`, `authViewModel.logout(context)` clear all UI state, Credential Manager state, and cancel active coroutine collectors immediately; `popUpTo(0)` wipes the backstack. |
| 2 | `FirebaseAuthRepository.logout()` invokes `ServiceLocator.clearAllUserSessions()` | `sessionGeneration` increments on all live repositories, every `ListenerRegistration` is removed, and all `StateFlow`s emit empty/default values before `auth.signOut()` runs. |
| 3 | User B logs in and queries `serviceRequests` or `serviceRequests/{aliceReqId}/messages` | `firestore.rules:100-156` checks `customerId == request.auth.uid` / `isRequestParticipant(requestId)`; because User B is a customer and not User A or an assigned mechanic, Firestore rejects access with `PERMISSION_DENIED`. |
| 4 | Verified via `firestore.test.js` (`15/15` passing) and `UserSessionIsolationTest` | **Handled Safely** |

#### Conclusion
**Fix Verified** - Neither User B's UI/memory cache nor direct Firestore requests can access User A's service requests, chat messages, audit events, or profile data across logout or account switching, while Mechanic Pro and Admin workflows remain functional.
