# Setting up SMART for your institution

This guide is for the developer who builds SMART for a research institution other than Princeton. You will run your own copy of the app under your institution's name, icon and app ID, with your own cloud accounts, and keep taking SMART releases as they come out.

The model is simple: **fork SMART, change only the brand files and your own configuration, and merge SMART releases into your fork.** Because your changes stay in a few files that SMART itself rarely touches, updating to a new SMART release is a normal `git merge`.

## What you will need

| Item | Notes |
|---|---|
| An app ID (applicationId) | For example `edu.example.studyapp`. Choose it once: see [Step 2](#step-2-change-the-brand-files). |
| App name and service labels | What participants see on the home screen and in Settings > Accessibility. |
| IRB-approved texts | Consent (disclaimer), welcome and "about this app" texts. |
| Policy links | Your Terms of Service and Privacy Policy URLs. |
| An icon | A square PNG. 512 x 512 works well. |
| AWS account | S3 bucket and Cognito pools. See [S3_BUCKET_CREATION.md](S3_BUCKET_CREATION.md), [USER_POOL_CREATION_AWS.md](USER_POOL_CREATION_AWS.md) and [IDENTITY_CREATION_AWS.md](IDENTITY_CREATION_AWS.md). |
| Firebase project | For Crashlytics and for distributing the app to participants. |
| A signing key | A release keystore you will keep for the life of the study. |

## Step 1. Fork SMART

SMART lives at [github.com/researchaccelerator-hub/princeton-smart](https://github.com/researchaccelerator-hub/princeton-smart), which is public.

- **A public fork** is the simplest: use GitHub's Fork button. Nothing secret is committed (AWS and Firebase settings are in gitignored files), so a public fork is safe.
- **A private repository:** GitHub forks of a public repository are always public. Create an empty private repository instead, then:

  ```bash
  git clone https://github.com/researchaccelerator-hub/princeton-smart.git
  cd princeton-smart
  git remote rename origin upstream
  git remote add origin https://github.com/<your-org>/<your-repo>.git
  git push -u origin main
  ```

Either way, keep a remote called `upstream` that points at SMART. You will merge releases from it.

## Step 2. Change the brand files

These are the only files your fork should change for branding:

| File | What to change |
|---|---|
| `brand.properties` (repo root) | `applicationId` |
| `app/src/main/res/values/brand.xml` | Every participant-visible text that names the app, the institution or the study team |
| `app/src/main/res/drawable/logo_just_square_small.png` | The icon. It is the launcher icon, the logo inside the app and the notification icon. Keep the file name. |

**The app ID.** It is the app's identity on the phone and in Firebase. Choose it before the first participant installs the app and never change it: a different app ID installs as a separate app, and participants lose any data that has not uploaded yet. Debug builds add `.debug` to it, so a debug and a release build can be installed side by side. The Kotlin source package stays `com.screenlake` whatever app ID you choose. Do not rename it; a renamed package turns every SMART update into hundreds of conflicts.

**brand.xml.** Keep every string name and change only the text. The file is grouped:

- App name and labels: `app_name` (home screen), `app_full_name` (welcome screen title), `screenshot_service_label` and `accessibility_service_label` (Settings > Accessibility and the running-services list), `log_export_label` (Send Logs, if enabled).
- Institution, consent and study texts: `welcome_intro`, `disclaimer`, `participant_info_what_body`, `privacy`. Use your IRB-approved wording.
- Your policies: `agreement_text` (the Terms of Service and Privacy Policy links on the registration screen) and `privacy_url` (the privacy page the app opens).
- Texts that name the app, such as "What is SRK" or "Logout of SRK". Replace the name in each.

Brand colors are optional: they are in `app/src/main/res/values/colors.xml`. Changing them is fine, but expect to resolve a conflict there now and then when you merge a SMART release.

## Step 3. Keep the Princeton Research Accelerator credit

Every build of SMART shows "Powered by the Princeton Research Accelerator" at the bottom of the welcome screen and as the last line in Settings. Keep it as it is: it is a condition of using SMART. It is the `powered_by` string in `app/src/main/res/values/strings.xml`, outside the brand file on purpose. Do not change or remove it; the unit tests check that it is present.

Also keep `LICENSE.md` unchanged, as the license requires.

## Step 4. Study settings

Study settings, such as screenshot frequency, image upload, the number of digits in the invite code (`INVITE_CODE_LENGTH`) and an optional user-exists endpoint, are in `app/src/main/java/com/screenlake/recorder/constants/ResearchConfig.kt`. The README's [Researcher Configuration File](../README.md#researcher-configuration-file) section lists every setting. This is a code file that SMART updates too, so change only the values you need.

## Step 5. Connect your cloud accounts

**AWS.** Put your values in `local.properties` at the repo root (gitignored):

```properties
AMAZON_REGION_NAME=us-east-1
AMAZON_BUCKET_NAME=your-bucket
COGNITO_IDENTITY_POOL_ID=us-east-1:...
COGNITO_POOL_ID=us-east-1_...
COGNITO_APP_CLIENT_ID=...
```

**Firebase.** In your Firebase project, add an Android app for your app ID, and a second one for `<your app ID>.debug` if you build debug versions. Download `google-services.json` and put it at `app/google-services.json` (gitignored). The build still works without it, but Crashlytics is off then.

Never commit either file.

## Step 6. Build and sign

Run the tests, which include the brand checks (`BrandFilesTest`):

```bash
./gradlew test
```

Then build a signed release APK in Android Studio (**Build > Generate Signed App Bundle or APK**), as the README's [Build the Signed apk and distribute over Firebase](../README.md#build-the-signed-apk-and-distribute-over-firebase) section describes.

Use the same keystore for every release. Android only installs an update over an existing install when it is signed with the same key. Store the keystore and its passwords outside the repository, somewhere your team will not lose them.

Keep SMART's `versionCode` and `versionName` in `app/build.gradle.kts`. They tell you, and us, which SMART release your build is.

## Step 7. Check your build

`BrandFilesTest` fails if a layout, the manifest, the settings screen or a string outside `brand.xml` still names SRK, Screenlake or Princeton, and if the credit is missing. Then install the app and check by eye:

- [ ] The home screen shows your app name and icon.
- [ ] The welcome screen shows your title, your intro text and the credit.
- [ ] Settings > Accessibility lists your accessibility label.
- [ ] Consent, "About this app" and privacy texts are your IRB-approved texts.
- [ ] Settings shows the credit as its last line.
- [ ] Registering a test participant uploads data to your S3 bucket.

## Getting SMART updates

SMART releases are tagged `vX.Y.Z` (for example `v1.53.0`). To take one:

```bash
git fetch upstream --tags
git checkout main
git merge v1.53.0
./gradlew test
```

If you changed only the files in this guide, conflicts can only appear in them, usually when SMART adds or rewords a brand string. Keep your text, and add any new string from SMART with your own wording. Then rebuild, test and sign as usual.

Read each release's notes for changes to study settings, the data schema (`DATA_SCHEMA_VERSION`) or the upload layout, since they can affect your analysis scripts.

## Moving from an existing copy of SMART

If your institution already runs its own copy of SMART, the steps above still apply. To keep current participants' installs and data:

- **Keep your app ID.** Put the app ID your participants have installed in `brand.properties`. You do not need SMART's package name or your old package name in the source: only the app ID matters to Android.
- **Sign with your existing key.** A different key means participants have to uninstall and reinstall, and lose data that has not uploaded.
- **Check the version code.** SMART's `versionCode` must be higher than the one your participants have installed, or Android refuses the update.
- **Check the database version.** Compare `DATA_SCHEMA_VERSION` in your copy with SMART's. The update keeps local data only if SMART can migrate from your version; the README's "For developers" note on the data schema says which versions it migrates from. If it cannot, ask participants to upload everything before they update.
- **Compare upload paths.** Check where your copy writes data in S3 and where SMART does (see the README), and update your download and analysis scripts if they differ.
- **Move your branding** into the brand files instead of your old edits. Anything else you changed in your copy: send it to SMART as a pull request if it is a fix others would want, or keep it as a small, documented change in your fork.

Test the update on a phone that has your current version installed with data that has not uploaded yet, before you send it to participants.

## Contributing back

Fixes and features that help every institution belong in SMART. Open a pull request against `researchaccelerator-hub/princeton-smart` from a branch in your fork. Keep institution-specific changes out of it.
