# Upload failures and sign-in recovery

How the app handles uploads that fail, and what happens when a participant's sign-in can no longer be renewed. Since 1.53.0, this combines SMART's AC-1043 work with the University of Minnesota's upload recovery changes.

## Kinds of failure

Each upload ends as one of four results (`UploadResult`). Only `SUCCESS` deletes the file from the phone.

| Result | Causes | What happens |
|---|---|---|
| `SUCCESS` | Uploaded | File and its database row are deleted; the consecutive-failure count resets |
| `TRANSIENT_FAILURE` | Phone offline, network error, timeout, HTTP 408/429/5xx, AWS server error or throttling, Cognito unreachable during a silent re-login | File kept; the run stops and WorkManager retries later |
| `AUTHENTICATION_FAILURE` | AWS rejects the identity, and Cognito rejected a silent re-login with the stored credentials | File kept; counted toward recovery (below) |
| `CONFIGURATION_FAILURE` | Other HTTP 4xx, AWS setup not loaded, participant has no invite code or panel yet | File kept; the run continues with the next file |

Errors are sorted by type and AWS error code (`UploadFailureClassifier`), never by matching words in the error message. An offline phone is never treated as an expired sign-in.

## When uploads run

There are two upload jobs: the periodic one, every `UPLOAD_WORKER_INTERVAL_HOURS` (1 by default), and one unique on-demand job (`UploadWorker.enqueue`, `WORK_MANAGER_UPLOAD_ON_DEMAND`). Asking for an upload while the on-demand job is waiting or running does not add a second one. The on-demand job is requested:

- when a zip run finishes, whether it succeeded or not (`ZipFileWorker`), so earlier zips upload too;
- after a participant signs in.

Zips are made by the hourly zip job and by one unique on-demand zip job (`ZipFileWorker.enqueueThenUpload`, `WORK_MANAGER_ZIP_ON_DEMAND`). The on-demand zip is requested after each OCR run (while the phone is locked, or when the participant taps Upload with recording on) and by the stats screen's Upload button while recording is stopped. A zip requested while one is running waits and runs after it, so nothing finished in the meantime waits an hour. Zipping needs no network.

With recording stopped, OCR cannot run (it runs in the recording service), so the Upload button zips and uploads only screenshots that already have their text. The rest upload after recording restarts.

While a run uploads, it shows a quiet "Uploading study data" notification (`UPLOAD_NOTIFICATION_ID`, channel "Data uploads") as a `dataSync` foreground job, so it is not stopped after WorkManager's usual 10 minutes. If Android does not allow the foreground start (Android 12 and later can refuse it while the app is in the background), the event log gets `UPLOAD_FOREGROUND_UNAVAILABLE` and the run continues as a normal background job. On Android 15 and later, `dataSync` jobs get at most 6 hours a day; WorkManager stops the run when that is reached and the next run continues.

Only one run uploads at a time. A run that finds another one uploading retries shortly after instead of failing. Files that become ready while a run is uploading are uploaded by that run before it ends, and each file is tried at most once per run.

A run skips while recovery is pending (below) or when no participant is signed in. OCR and zipping continue during recovery, so the data is ready when the participant signs in again.

## One credential fetch per run

At the start of each upload run, the app loads AWS credentials once (`Util.prepareUploadSession`) and signs every upload in the run with them.

If loading fails while the phone is online and the error is not a network or server error, the app signs in again silently with the participant's stored credentials and tries once more. It also does this when the credential call hangs, which happens when a session was revoked on the server; each AWS call has a 15-second timeout.

A silent re-login reports whether Cognito rejected the stored credentials or could not be reached (`SignInOutcome`). Only a rejection counts as a credential failure; an unreachable or throttled Cognito, or a network error on the credential fetch that follows, is temporary.

A run uploads up to 100 files; files that fail without being retryable (for example, no invite code yet) do not use up that limit, so the files after them are still tried. When more files remain after 100 successful uploads, the run is retried.

If AWS rejects an upload with 401 or 403 partway through a run (for example, credentials expired mid-run), the app signs in again silently and retries that file once.

## Recovery

Every upload run that ends with `AUTHENTICATION_FAILURE` counts once. After `CREDENTIAL_FAILURE_NOTIFICATION_THRESHOLD` (6) consecutive failing runs, `AuthRecoveryCoordinator` begins recovery, once:

1. A persisted "recovery pending" flag stops further upload attempts until the participant signs in. This prevents retry loops.
2. A "Sign in to resume uploads" notification opens the login screen. If notifications are blocked, the login screen shows the same message the next time it opens.
3. The participant is signed out and recording stops. Data stays on the phone: pending zips, screenshots, the database and log files are kept. The app records whose data it is (email, invite code, tenant and panel).
4. If the app is killed partway through, recovery is finished at the next sign-in: the same account resumes, a different one gets the warning below.
5. While recovery is pending, the recording watchdog does not show its "Study Recording Stopped" notice, so the sign-in notice comes first.

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
| `UPLOAD_FOREGROUND_UNAVAILABLE` | Android did not allow the upload notification; the run continued in the background |
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
| Run outcome, WorkManager result, the unique upload job and its notification | `recorder/services/UploadWorker.kt` |
| Zip, then request an upload | `recorder/services/ZipFileWorker.kt` |
| Upload button on the stats screen | `ui/fragments/ScreenshotStatsFragment.kt` |
| Threshold, notification, sign-out | `recorder/services/AuthRecoveryCoordinator.kt` |
| Sign-out keeping data, reconnecting the same or a different account | `data/repository/GeneralOperationsRepository.kt` |
