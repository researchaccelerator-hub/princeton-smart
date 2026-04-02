# Princeton SMART — Researcher Quickstart

This guide is for researchers who want to deploy the Princeton SMART app at their institution. It gives you a plain-language overview of what's involved, what you'll need, and how long it will take — so you can plan accordingly before diving into the full [README](README.md).

---

## What is this?

Princeton SMART (Screenlake Mobile Analytics & Recording Tool) is an Android research app that passively captures screenshots every 5 seconds on participants' smartphones, extracts text from those screenshots, and securely uploads all collected data to your institution's private AWS cloud storage. The app also logs which apps are in use, session activity, and accessibility data.

**What data is collected:**
- Screenshots and OCR-extracted text
- App usage logs (app name, timestamps, session durations)
- App segment history (the last 5 apps on screen)
- Accessibility view data
- Session start/end times

**What data is NOT collected:** location, microphone, camera, or speakers.

All data goes exclusively to the AWS infrastructure you configure — there is no shared or default cloud destination built into the app.

---

## What will participants experience?

Participants receive an email from Firebase with a link to install the app. After installing, they:

1. Register with their email and a panel code you provide
2. Complete a one-time permission-granting flow (usage access, battery optimization, accessibility service) — this requires navigating system settings on their phone, guided by the app
3. The app then runs passively in the background with no further interaction required

---

## What do you need before starting?

| Requirement | Notes |
|------------|-------|
| AWS account (with billing enabled) | All data storage and authentication runs through AWS |
| Firebase account | Free tier is sufficient; used to distribute the app to participants |
| A Mac or Windows computer | For running Android Studio to build the app |
| Android Studio installed | Free download from [developer.android.com/studio](https://developer.android.com/studio) |
| An Android test device (Android 9+) | Recommended for testing before distributing to participants |
| PyCharm or Python environment | For running post-processing scripts to retrieve and organize data |
| Basic comfort with web consoles | You'll navigate AWS and Firebase web UIs — no coding required |

**Optional but useful:**
- University branding assets (logos, colors) for customizing the app's appearance
- A sentiment dictionary for text analysis (see README for recommendation)
- Familiarity with Google Play if you want broader public distribution beyond Firebase

---

## How long does setup take?

| Phase | What you're doing | Est. Time | Difficulty |
|-------|-------------------|-----------|------------|
| 1 – AWS Infrastructure | Create S3 bucket and two Cognito pools | 30–60 min | Moderate |
| 2 – Firebase Setup | Create project, enable App Distribution | 15–30 min | Low |
| 3 – Build the App | Configure credentials and compile the APK in Android Studio | 30–60 min | Moderate |
| 4 – Distribute to Participants | Upload APK to Firebase, send install link | 15 min | Low |
| 5 – Retrieve and Process Data | Run Python script to download and unzip data from S3 | 20–30 min | Low |
| **Total** | | **~2–3 hours** | |

> **Note on Phase 3 difficulty:** Building the app requires Android Studio and entering your AWS credentials into a config file. No programming knowledge is needed, but you should feel comfortable following click-by-click software instructions. Video walkthroughs are available (linked in each phase below).

---

## Step-by-step checklist

Work through these phases in order. Each phase links to the detailed guide for that step.

---

### Phase 1: AWS Infrastructure (~30–60 min)

AWS is the cloud service where participants' data will be stored. You'll create three resources:

- [ ] **Create an S3 bucket** — the secure storage bucket where screenshots and data files will land
  → Follow [docs/S3_BUCKET_CREATION.md](docs/S3_BUCKET_CREATION.md)

- [ ] **Create a Cognito User Pool** — manages participant accounts and authentication
  → Follow [docs/USER_POOL_CREATION_AWS.md](docs/USER_POOL_CREATION_AWS.md)

- [ ] **Create a Cognito Identity Pool** — connects authenticated participants to your S3 bucket
  → Follow [docs/IDENTITY_CREATION_AWS.md](docs/IDENTITY_CREATION_AWS.md)

- [ ] **Record your AWS credentials** — you'll need these four values in Phase 3:
  - AWS region (e.g., `us-east-1`)
  - S3 bucket name
  - Cognito Identity Pool ID
  - Cognito User Pool ID and App Client ID

---

### Phase 2: Firebase Setup (~15–30 min)

Firebase is used to distribute the app to participants via email invite.

- [ ] Go to [console.firebase.google.com](https://console.firebase.google.com) and create a new project
- [ ] In **Project Settings**, add a new Android app with package name `com.screenlake`
- [ ] Download the `google-services.json` file — keep this file handy for Phase 3
- [ ] Go to **Run → App Distribution → Testers & Groups**
- [ ] Create a tester group and add your participants' email addresses

---

### Phase 3: Build the App (~30–60 min)

> Video walkthrough: [SRK Build Tutorial pt1](https://youtube.com/video/XDHoJhoESPc)

- [ ] Open Android Studio → **File → New → Project from Version Control** → paste the repository URL → **Clone**
- [ ] Locate the `local.properties` file in the project root and add these five lines:

  ```
  AMAZON_REGION_NAME=<your-aws-region>
  AMAZON_BUCKET_NAME=<your-bucket-name>
  COGNITO_IDENTITY_POOL_ID=<your-identity-pool-id>
  COGNITO_POOL_ID=<your-user-pool-id>
  COGNITO_APP_CLIENT_ID=<your-app-client-id>
  ```

  > **Security:** Never commit `local.properties` to a public repository. These are private credentials.

- [ ] Copy `google-services.json` (from Phase 2) into the `app/` folder of the project
- [ ] Optional: Press **▶** in Android Studio to test the app on the built-in emulator
- [ ] Go to **Build → Generate Signed App Bundle or APK → APK**, follow the prompts, and select **release** when asked
- [ ] Locate the generated `.apk` file on your computer

---

### Phase 4: Distribute to Participants (~15 min)

- [ ] Return to the [Firebase console](https://console.firebase.google.com) → **Run → App Distribution → Releases**
- [ ] Drag and drop your signed `.apk` file into the upload area
- [ ] Select your tester group from Phase 2
- [ ] Click **Distribute** — participants will receive an email with an install link

> **Note:** Participants do not need the Firebase Tester app. The install link in their email is sufficient.

---

### Phase 5: Retrieve and Process Data (~20–30 min)

> Video walkthrough: [SRK Build Tutorial pt3](https://youtube.com/video/oFH0MieGgUY)

When participants' data arrives in S3, it is in compressed (zip) format. Use the included Python script to download and organize it.

- [ ] Read the [scripts/README.md](scripts/README.md) to understand the processing pipeline and install dependencies
- [ ] Run the script from an approved machine where participant data may be temporarily stored (an AWS EC2 instance is recommended — it avoids laptop sleep interruptions and keeps data within your institution's approved infrastructure)
- [ ] Review the output: unzipped screenshots, OCR text, and consolidated CSV files

> **Tip:** The script is idempotent — if it stops mid-run, you can safely re-run it from the beginning.

---

## What if I get stuck?

- **Full setup documentation:** [README.md](README.md) — includes more detail on every step above
- **AWS sub-guides:** [docs/](docs/) folder
- **Questions or issues:** Open an issue on the [GitHub repository](https://github.com/researchaccelerator-hub/princeton-smart/issues)
- **Researcher guide (non-technical):** [Princeton SMART: A Practical Guide](https://docs.google.com/document/d/1TMU9V169so5C-JHJscVokS_iKi2IjCiabze07KnQ41E/edit?usp=sharing)
