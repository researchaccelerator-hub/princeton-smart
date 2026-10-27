# Upload failures and sign-in recovery

How the app handles uploads that fail, and what happens when a participant's sign-in can no longer be renewed. Since 1.53.0, this combines SMART's AC-1043 work with the University of Minnesota's upload recovery changes.

## Kinds of failure

Each upload ends as one of four results (`UploadResult`). Only `SUCCESS` deletes the file from the phone.

| Result | Causes | What happens |
|---|---|---|
| `SUCCESS` | Uploaded | File and its database row are deleted; the consecutive-failure count resets |
| `TRANSIENT_FAILURE` | Phone offline, network error, timeout, HTTP 408/429/5xx, AWS server error | File kept; the run stops and WorkManager retries later |
| `AUTHENTICATION_FAILURE` | AWS rejects the identity, and a silent re-login with the stored credentials did not fix it | File kept; counted toward recovery (below) |
| `CONFIGURATION_FAILURE` | Other HTTP 4xx, AWS setup not loaded, participant has no invite code or panel yet | File kept; the run continues with the next file |

Errors are sorted by type and AWS error code (`UploadFailureClassifier`), never by matching words in the error message. An offline phone is never treated as an expired sign-in.

## One credential fetch per run

At the start of each upload run, the app loads AWS credentials once (`Util.prepareUploadSession`) and signs every upload in the run with them.

If loading fails while the phone is online and the error is not a network or server error, the app signs in again silently with the participant's stored credentials and tries once more. It also does this when the credential call hangs, which happens when a session was revoked on the server; each AWS call has a 15-second timeout.

If AWS rejects an upload with 401 or 403 partway through a run (for example, credentials expired mid-run), the app signs in again silently and retries that file once.

## Recovery

Every upload run that ends with `AUTHENTICATION_FAILURE` counts once. After `CREDENTIAL_FAILURE_NOTIFICATION_THRESHOLD` (6) consecutive failing runs, `AuthRecoveryCoordinator` begins recovery, once:

1. A persisted "recovery pending" flag stops further upload attempts until the participant signs in. This prevents retry loops.
2. A "Sign in to resume uploads" notification opens the login screen. If notifications are blocked, the login screen shows the same message the next time it opens.
3. The participant is signed out and recording stops. Data stays on the phone: pending zips, screenshots, the database and log files are kept. The app records whose data it is (email, invite code, tenant and panel).
4. While recovery is pending, the recording watchdog does not show its "Study Recording Stopped" notice, so the sign-in notice comes first.

When the participant signs in again:

- **Same account:** their invite code and panel are restored, recovery ends, and an upload starts.
- **Different account:** the login screen warns that signing in deletes the previous participant's data that has not uploaded yet. If they continue, that data is deleted before the new account's data is collected. If they cancel, they are signed out and can sign in with the original account.

## Signing out from Settings

Settings > Logout offers three choices:

- **Sign out and keep data:** works like recovery. The data stays and uploads when the same account signs in again; a different account gets the same warning before it is deleted.
- **Delete data and sign out:** asks for confirmation, then deletes all collected data and the saved invite code from the phone.
- **Cancel.**

## Event log entries

These events appear in the uploaded event log. They never include credentials, presigned URLs, passwords or raw responses.

| `event` | Meaning |
|---|---|
| `UPLOAD_CREDENTIAL_FAILURE` | Credentials could not be renewed; `msg` gives the reason and the consecutive count |
| `UPLOAD_AUTH_FAILURE_CONFIRMED` | AWS rejected an upload (`http_401`, `http_403`) or identity |
| `UPLOAD_AWS_TRANSIENT_FAILURE` | Offline, network or server error; will retry |
| `UPLOAD_AWS_CONFIGURATION_FAILURE` | The app's AWS setup could not be loaded |
| `UPLOAD_HTTP_FAIL` | Any failed upload response, with the HTTP code |
| `AUTH_RECOVERY_STARTED` | Recovery began; the participant was signed out |
| `AUTH_RECOVERY_NOTIFICATION_SHOWN` | The sign-in notification was shown |
| `AUTH_RECOVERY_LOGIN_SUCCEEDED` | The participant signed in again during recovery |
| `REAUTH_DIFFERENT_ACCOUNT_CONFIRMED` / `_CANCELLED` | The participant confirmed or cancelled signing in with a different account |
| `MANUAL_LOGOUT_DATA_RETAINED` | The participant signed out and kept their data |

## Code

| Piece | File |
|---|---|
| Credential loading, silent re-login, presigned URLs | `recorder/upload/Util.kt` |
| Error sorting | `recorder/upload/UploadFailureClassifier.kt` |
| Upload results, HTTP status mapping, retry after 401/403 | `recorder/services/RealUploadHandler.kt` |
| Run outcome and WorkManager result | `recorder/services/UploadWorker.kt` |
| Threshold, notification, sign-out | `recorder/services/AuthRecoveryCoordinator.kt` |
| Sign-out keeping data, reconnecting the same or a different account | `data/repository/GeneralOperationsRepository.kt` |
