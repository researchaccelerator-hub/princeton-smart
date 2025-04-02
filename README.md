# Screenlake Research Kit

## Explanatory Research Guide for Faculty, PIs, ICs, and Researchers
- [Screenlake Research Kit: A Practical Guide](https://docs.google.com/document/d/1TMU9V169so5C-JHJscVokS_iKi2IjCiabze07KnQ41E/edit?usp=sharing)


## Table of Contents
1. [High level information](#high-level-information)
2. [Preparing Connections for the Application](#preparing-connections-for-the-Application)
3. [Build and Distribute the App Using Android Studio and Firebase](#build-and-distribute-the-app-using-android-studio-and-firebase)
4. [Using the App on your phone](#using-the-app-on-your-phone)
5. [Unzipping and consolidation of zip files in the cloud](#unzipping-and-consolidation-of-zip-files-in-the-cloud)
6. [Configurable Resources](#configurable-resources)
7. [How to Cite the Screenlake Research Kit](#how-to-cite-the-screenlake-research-kit)

## High Level Information

1. [Contributing](docs/CONTRIBUTING.md)
2. [Code of Conduct](docs/CODE_OF_CONDUCT.md)

### Terminology
- **Researcher**: The person most likely reading this right now / the team using this repo to compile and distribute a data-collection app for research purposes.
- **Participant**: The person who will be installing the resulting Android app on their device, so as to participate in Researcher's research. May also be stated as app 'user' as in 'AWS userpool'

### Necessary Resources to match this tutorial

This step-by-step guide is made to match the following tools, including click-by-click UI navigation instructions, as of 2025-03-31. Each of these tools can be substituted with alternative replacements (e.g. a different cloud provider) by a technical user of the repo, such as a software engineer or programming-saving student. If you're a social scientist who wants to get going fast and independently, then you'll want to use these tools and stick to the readme as-is.

- [**AWS**](https://aws.amazon.com/), a cloud provider where screenshtos and data land
- [**Android Studio**](https://developer.android.com/studio), a software for compiling an Android App
- [**Firebase**](https://firebase.google.com/), a tool for distributing apps
- [**Pycharm**](https://www.jetbrains.com/pycharm/download), a tool for running post-processing Python scripts
- A safe local machine (like a university-approved laptop) or virtual machine (like an [ec2](https://aws.amazon.com/pm/ec2/)) that can connect to your cloud storage location.
   - When participants' data lands in the cloud, it's in zipped-format, and you'll run some (included) post-processing Python scripts to unpack and organize all of that data. Whereever you run the post-processing scripts from, participant data be be downloaded to temporarily.


### Not necessary, but useful to have ready
- A **sentiment dictionary** file for sentiment post-analysis. We recommend using the 'valence' variable of the NRC Valence, Arousal, and Dominance Lexicon created by Dr. Saif M. Mohammad and Dr. Peter Turney at the National Research Council Canada ([download link](https://saifmohammad.com/WebDocs/Lexicons/NRC-VAD-Lexicon.zip))
- **Branding assets** and graphic standards for your university, so you can confidently replace the default logos and names with your own, and ensure your research participants know what they're looking at.
- **An Android smartphone** so you can test out your app yourself on a real phone before sending it to participants.
- Familiarity with [**Google Play**](https://play.google.com/console/about/topics/get-started/) in case you'd like to make your app publically accessible to potential research participants. The tutorial will only show you how to use Firebase for targetted distribution of the app, i.e., direct-send of an app to someone's email address. Either method of distribution has its advantages, but getting an app onto the Google Play store is a bit more involved.

### Overview of App Technical Capacities
The contents of this repository enable you (a researcher) to create an Android application. The Android application is designed to efficiently, continuously, & passively take screenshots of the smartphone device it is installed on, every 3 seconds until manually interrupted, and also perform Optical Character Recognition (OCR) to extract text from the captured images. Additionally, the app collects a variety of data related to the smartphone device and the apps running on it (listed below):

- **Screenshots & OCR Processing:** Captures a full-screen screenshot every 3 seconds, and uses OCR to extract text from the screenshots.
- **App Log Data:** Records metadata such as app names and timestamps at the time of each screenshot.
- **App Usage Data:** Collects detailed usage data from the device, including which apps are running and how long they’ve been used.
- **App Segment Data:** Tracks and logs data showing the last five apps that were on the screen before the current one.
- **Smartphone Session Data:** Monitors smartphone session data, logging the time that the phone is unlocked to when it’s locked.
- **Accessibility Root View Data:** Captures so-named accessibility data from the root view of the app to enhance data collection. Essentially, all XML and actions.
- **Cloud Upload & Configuration:** All collected data is uploaded to the cloud. The app is configurable to work with your own cloud infrastructure, specifically AWS. The repository also includes scripts that can be used to unpack and organize data uploaded to the cloud.

### Technical Constraints for Participants' Data Privacy

In its original form, this repository does not include a default/hard-coded cloud destination for data sent off of participants' smartphones. You, the researcher, must configure and designate a data destination for the system to work, i.e., the cloud infrastructure of the researcher's home instution. Collected data only goes where you configure the app to send that data.

In its orignal form, this app is not capable of accessing the microphone, camera, speakers, or location services of the smartphone.

### Setup Walkthrough videos
- [SRK Build Tutorial pt1: Setup and Build](https://studio.youtube.com/video/XDHoJhoESPc/edit) (illustrates [Section 3](#build-and-distribute-the-app-using-android-studio-and-firebase))
- [SRK Build Tutorial pt2: The App](https://studio.youtube.com/video/SbhaQb0CChE/edit) (illustrates [Section 4](#build-and-distribute-the-app-using-android-studio-and-firebase))
- [SRK Build Tutorial pt3: Scripts](https://studio.youtube.com/video/oFH0MieGgUY/edit) (illustrates [Section 5](#unzipping-and-consolidation-of-zip-files-in-the-cloud))


## Preparing Connections for the Application

### Step 1. Configure an AWS cloud infrastructure to receive data from your soon-to-be-built app.

Estimated time: 10 minutes.

We assume you're using AWS. You can use other cloud platforms, but you'll need to map these explicit instructions to their analogous set-up steps in another service.
Follow the instructions in these three markdown files, in order.

#### Create Cognito User Pool
In order to authenticate participants, you need to create a userpool in AWS. Follow the [user-pool-creation-aws.md](docs/USER_POOL_CREATION_AWS.md) tutorial.

#### Create S3 Bucket for Storage
In order to store screenshots and data sets, you need to create an S3 in bucket in AWS. Follow the [s3-bucket-creation-aws.md](docs/S3_BUCKET_CREATION.md) tutorial.

#### Create Cognito Identity
In order allow paticipants access to AWS resources, you need to create a cognito identity in AWS. Follow the [identity-creation-aws.md](docs/IDENTITY_CREATION_AWS.md) tutorial.


---

### Step 2. Configure Firebase to enable distribution of your soon-to-be-built app listing.
Estimated time: 5 minutes.

You will need to create a Firebase project and download the `google-services.json` file to configure the app for Firebase services. Firebase is a tool for distirbuting applications to people. It can be used on its own for app distirbutions or as a stepping stone to creating a Google Play Store
1. Create a Firebase project and then go to the [Firebase Console](https://console.firebase.google.com/) and select your project.
2. Navigate to **Project Settings** by clicking the gear icon in the sidebar.
3. In the **Your Apps** section, add one a new Android app.
4. Name the package exactly 'com.screenlake', with whatever nickname you'd like, and register.
   - Notes: 'Package name' is the literal file name of the app's undelying file (extension = .apk). A .apk file is to Android what  an executable (.exe) is on Windows. The naming convention for apk files is like a reverse-order website name, like "com.blah.blah".
   - DANGER: If you want to name the package something unique besides com.screenlake, either for branding purposes or as a necessary step to enable a Google Play launch down the road, then you can name the package something else like com.yourproject.
   - DANGER ctd.: If you choose to do that, then you will need to take care to replace every reference to 'com.screenlake' in the repo with your own unique package name. This includes references within file contents, within file names, and within the cache, and should be done carefully and exhaustively to avoid errors.
   - You do not need to have a custom package name to distribute your app via Firebase, which is sufficient for distributing the app to a panel of participants.

4. Download the `google-services.json` file provided during the app registration process, and then click through the rest of the app creation workflow until the app is created. (Don’t worry about the Firebase SDK setup instructions you’ll see there).

5. Create a Firebase tester group, which is a set of contacts you can send your app to (i.e., your research participants).
   - In the left-side panel in the main Firebase UI, expand out **Run** and then select **App Distribution**.
   - Click **Get Started** (if this is your first time).
   - Inside the App Distribution workpage, you'll see the **Tester & Groups** tab. Select that tab,
   - In the new page, click **Add group** and use the resulting menu to name your new group (e.g., "testgroup1"). You can then click "Add tester" to add contacts to your new tester group one by one (i.e., add their email address to the group), or use the three-dot-kebab icon to import long lists of testers's email addresses into your group (e.g., that's your list of research participants).
   - Adding testers' email address here doesn't cause any email to be sent. Instead, it just enables you to send-out the app directly to those contacts after it's been build.



## Build and Distribute the App Using Android Studio and Firebase
Estimated time: 10 minutes.

You now have all of the technical assets necessary to build the app & distribute it, using Android Studio. In this section, we'll go over how to do that. In later sections, we'll go through configuration options, testing UI customization / branding, and data processing in the cloud.

In this step, you'll use the software called Android Studio to transform the repository of files into a functioning app, i.e., a .apk file. The steps are easy, and include plugging-in inputs to connect your app to your own AWS setup and your own Firebase setup.

At this point, there's a lot of Android Studio GUI utilization, so you can/should reference this [10-minute visual walkthrough video.](https://youtu.be/XDHoJhoESPc)



### Bring the Repo into Android Studio

1. On the repo's github webpage, look for the button **code**. Click on it to reveal the URL for the Github page. Copy that URL.
2. Open up Android Studio. In the topbar, click **File** >> **New** >> **Project from Version Control**
3. In the small window that pops up, paste the githiub URL into the URL field. Click **Clone**
4. When prompted, open the project in a **New Window**
5. Give Android Studio a minute or so to sync everything up and load. Some harmless error warnings may pop-up and be resolved automatically during this process.
6. The repo's file directory will appear on the left side of the Android Studio interface. By default, the file directory zooms-in on the "app" subfodler of the whole repo. For an aerial view, you can get that file viewer "up" to the top-level of the repo by clicking on the bold word "Android" and selecting "Project" instead.


---

### Configure the App to link up with your AWS setup
Now it's time to edit these files ever so slightly in order to connect your app to your AWS setup. That is, you will need to configure the app to work with your AWS resources.

- Within the top-level directory, you'll find a file called local.properties. Double-click that filename so it appears in a file editor window within Android Studio.
- You'l see that the file has only one functional line. We're going to add 5 more lines.
- Paste in the the following 5 lines:


```
AMAZON_REGION_NAME=
AMAZON_BUCKET_NAME=
COGNITO_IDENTITY_POOL_ID=
COGNITO_POOL_ID=
COGNITO_APP_CLIENT_ID=
```
Now, for each field, the answer is an ID or name that connects back to your recently-created AWS setup from Step 1. See descriptions or each field below. Note that you do not need to encase the input values in quotes. e.g., if your bucket name is literally mybucket, then you would enter,

```
AMAZON_BUCKET_NAME=mybucket
```

##### AMAZON_REGION_NAME:
The AWS region where your Cognito User Pool, Identity Pool, and S3 bucket are hosted (e.g., us-west-1).

##### AMAZON_BUCKET_NAME:
The name of your Amazon S3 bucket used for storing files or data (e.g., my-app-bucket).

##### COGNITO_IDENTITY_POOL_ID:
The unique ID for your Cognito Identity Pool, enabling federated identity and temporary AWS credentials (e.g., us-west-1:12345678-90ab-cdef-1234-567890abcdef).

##### COGNITO_POOL_ID:
The unique identifier for your Cognito User Pool used to manage and authenticate participants (e.g., us-west-1_ABCdEfghI).

##### COGNITO_APP_CLIENT_ID:
The ID assigned to your application for integrating with the Cognito User Pool (e.g., 12345abcdef67890).

##### SECURITY WARNING
Do not check-in local.properties to any non-private versioning system, this information should remain private. I.e., don't accidentally publish these values you just entered.


---

### Configure the App to link with your Firebase setup

1. Copy the `google-services.json` file that you downloaded from Firebase.
2. Paste it into the `app/` directory of your Android project:
   ```
   <project-root>/
   ├── app/
   │   ├── google-services.json
   ```


### Build the App and run on an emulator inside Android Studio

Give it try! You're all set to click the **▶** button in the top bar of the Android Studio interface. This will launch the application on an emulator (a simulated phone inside a computer) within your Android Studio. See the walkthrough video linked above for illustration.


### Build the Signed apk and distribute over Firebase

Now that you've seen the app running on an emulator, let's get it out into the world.

**Build it**
1. In the top bar of Android Studio, select **Build** >> **Generate Signed App Bundle or APK...** >> **APK**.
2. Follow the prompts in the small input window that pops up, including creation and protection of a Key Store if needed. You can see the walkthrough video for an illustration, but you should be fine to simply follow the prompts of the user interface.
3. At the end of the process for generating a signed apk, you'll be asked to choose if this is a debug build or a release build. Select **release**, and click **Create**. This will take aabout a minute to complete.
4. Android studio will give you a notification that the signed apk file has been build. Locate the signed apk file on your machine.

**Distribute it**
1. Return to the Firebase website, and go to **Run** >> **App Distribution** >> **Releases**.
2. Drag-and-drop your new signed apk file into the drag-and-drop location spot on the website.
3. Once it's uploaded, you should see a menu that allows you to Add testers. Use the dropdown there to select your test group from before.
4. Click **Next**, add a message you'd like to say to your testers. Finally, when you click **Distribute**, everbody in the test group or list of testers will receive an email from Firebase, inviting them to install the app.
   - Note that there is a dedicated Firebase-app-tester app, and Firebase might advertise that to you. Your test group (participants) do not need to install or use that Firebase app tester app.



### Manage Distribution

- You can manage your releases and tester groups from the [Firebase console](https://firebase.google.com/docs/app-distribution). Here, you can see who has installed your app, resend invites, and view feedback from testers.




### Collect Feedback

- **Receive Feedback:**
   - Testers can provide feedback directly through the app, which is sent to the Firebase console. This feedback helps you identify and fix issues before your app is publicly released.

- **Review Crash Reports:**
   - If your app crashes during testing, Firebase Crashlytics will automatically collect and report the crash data, allowing you to debug and improve your app’s stability.




## Using the App on your phone

It's best to see the app experience in video form. [Watch a visual walkthrough here](https://youtu.be/SbhaQb0CChE) to understand the participant experience.


Suggested commit: update the readme to include a walkthrough transcription or explanation of the app & it's interface.

## Unzipping and consolidation of zip files in the cloud

When participants' data lands in the cloud, it's in zipped-format. Use the python script in the **scripts** folder to unpack and organize all of that data.

NOTES:
- Whereever you run the script from, participant data be be downloaded to temporarily. So, make sure that you run that script from an approved machine which participant data can safely be stored. An AWS ec2 is likely your best option, as it falls under the same security and institutional approval umbrella as the AWS cloud storage you're using.
- The script can run for a long time if you've got a lot of data collected and process it all at once. The script will stop/hang if its connection is interrupted, (e.g.) if your laptop falls-asleep midway. You can mitigate this by:
   - Running processing in date-bounded batches (super easy thanks to the script)
   - Making sure that there are no internet disruptions during the script's run
   - Choosing to use an ec2 versus a local laptop, as the laptop is more prone to interruptions
- The script is idempotent, meaning that even it gets stuck or hung up, you can just cancel the run midway and re-run it with no harm done and no duplicated data in your output.

Check out the readme file inside of the **scripts** folder as well.
[Watch a visual valkthrough here.](https://youtu.be/oFH0MieGgUY).


To understand the final output data, read through the explainer doc.
[Screenlake Research Kit: A Practical Guide](https://docs.google.com/document/d/1TMU9V169so5C-JHJscVokS_iKi2IjCiabze07KnQ41E/edit?usp=sharing)



## Configurable Resources

Before using Screenlake Research Kit for your research, you'll want (or need) to configure some resources to reflect your particular research project. That means adding your app's privacy policy link, your app's terms of service link, editing the text on screen, filling-in the right logo and university name, confirming authentication setup, and so on.


### Authentication
This app uses authenticated user pools within AWS to provision accounts for participants. Participants will need to provide the app with an email and a panel confirmation code (aka group code) to use the app. The researcher determines the panel confirmation code. See screenshot of the participant-facing Android application's UI:

![alt text](<images/sign_up.png>)

The following fields are configurable.
#### panelConfirmationCode
This code identifies your participants. This is important because participants' emails will not be associated with participant data, only the code entered here. The code must be a 4 digit number.

### Privacy Policy and Terms of Service
The field **agreement_text** in the string.xml contains two URLs pointing to your (the researcher's) app's Privacy Policy and Terms of Service. If you plan to distribute via the Google Play Store, see their Privacy Policy and Terms of Service requirements. Stay in line with laws and regulations as well.

### Explainer / In-app explainer text
The user interface below (really, the text within it) is configurable and lets participants know during onboarding what the app is used for.

![alt text](<images/onboarding_explainer.png>)


### How to Change the App Logo in an Android App

#### Step 1: Prepare Your Logo
1. **Design Your Logo**: Create or choose a logo image that represents your app. Ensure it is in PNG format with a transparent background for best results.
2. **Resize the Logo**: Prepare different sizes of your logo to accommodate various screen resolutions. Common sizes include:
   - **48x48 px** for low-density screens (ldpi)
   - **72x72 px** for medium-density screens (mdpi)
   - **96x96 px** for high-density screens (hdpi)
   - **144x144 px** for extra-high-density screens (xhdpi)
   - **192x192 px** for xx-high-density screens (xxhdpi)
   - **512x512 px** for the Google Play Store icon

#### Step 2: Locate the Drawable Folder
1. **Open Android Studio**: Launch the app in Android Studio.
2. **Navigate to `res/drawable`**: In the project explorer, go to `app > src > main > res > drawable`. This is where you’ll store your logo files.

#### Step 3: Replace the Existing Logo
1. **Copy Your Logo Files**: Copy your resized logo files into the respective drawable folders (`drawable-ldpi`, `drawable-mdpi`, `drawable-hdpi`, etc.). If these folders don't exist, you can create them under the `res` directory.
2. **Rename the Files**: Ensure your logo files are named consistently across all drawable folders. The common name used is `ic_launcher.png`.
   - Example: `ic_launcher.png` for all resolutions.

#### Step 4: Update the AndroidManifest.xml
1. **Open `AndroidManifest.xml`**: In the project explorer, navigate to `app > src > main > AndroidManifest.xml`.
2. **Locate the `<application>` Tag**: Inside the `<application>` tag, find the `android:icon` attribute.
3. **Set the New Icon**: Change the value of `android:icon` to the name of your new logo (without the file extension).
   - Example: `android:icon="@drawable/ic_launcher"`

#### Step 5: Update the Adaptive Icons (Optional)
1. **Adaptive Icons**: For Android 8.0 (API level 26) and above, you should also update the adaptive icons. These consist of a foreground and background layer.
2. **Navigate to `res/mipmap-anydpi-v26`**: This folder contains the adaptive icon XML files.
3. **Edit the XML Files**: Modify `ic_launcher.xml` and `ic_launcher_round.xml` to point to your new foreground and background images.

#### Step 6: Rebuild the Project
1. **Rebuild the Project**: Go to `Build > Rebuild Project` to apply the changes.
2. **Run Your App**: Deploy the app to an emulator or a device to see the new logo in action.

#### Step 7: Test Your Logo
1. **Check Across Devices**: Test the logo on different devices and screen resolutions to ensure it looks good everywhere.


### Changing In-App Text in the `res/strings` Resource Folder

1. **Locate the `strings.xml` File:**
   - The `strings.xml` file is located in the `res/values/` directory of our Android project. This file stores all the text used in the app as string resources.

2. **Open `strings.xml`:**
   - In Android Studio, navigate to the `res/values/strings.xml` file and open it.

3. **Modify or Add New String Resources:**
   - To change existing text, find the corresponding `<string>` element by its name and edit the text between the tags. For example:
     ```xml
     <string name="action_sign_in">Sign in or register</string>
     ```
   - To add a new string, create a new `<string>` element:
     ```xml
     <string name="action_sign_in">Sign in or register now</string>
     ```

### Changing Languages
The app's user interface can be automatically translated into different languages based on the default language settings of the device it's installed on. You just have to provide the translations of each string.

1. **Create String Resources for Different Languages:**
   - For each language, create a new `strings.xml` file in a separate folder within `res/`. These folders are named using language and region codes, like `res/values-fr/` for French or `res/values-es/` for Spanish.
   - Example folder structure:
     ```
     res/
     ├── values/
     │   └── strings.xml
     ├── values-fr/
     │   └── strings.xml
     ├── values-es/
     │   └── strings.xml
     ```

2. **Add Translations:**
   - In each `strings.xml` file, provide the translation for each string. Ensure that the `name` attribute remains the same across all translations.
     ```xml
     <!-- English (default) -->
     <string name="welcome_message">Welcome to My Awesome App!</string>

     <!-- French -->
     <string name="welcome_message">Bienvenue dans mon application géniale!</string>

     <!-- Spanish -->
     <string name="welcome_message">¡Bienvenido a mi aplicación increíble!</string>
     ```

3. **Testing Language Change:**
   - To test your app in different languages, you can change the language setting on your Android device:
      - Go to **Settings** > **System** > **Languages & input** > **Languages**.
      - Add a new language or drag a preferred language to the top of the list.
   - The app should automatically load the appropriate `strings.xml` file based on the device's language setting.


### Add a Sentiment Dictionary

This repo does not have a sentiment dictionary built-in, as the best open-source sentiment analysis dictionaries for academic use prohibit reproduction and re-sharing of dictionaries whole-cloth. For post-processing per-screenshot sentiment values from OCR text or accessibility-services-derived text, wWe suggest using the valence variable within the txt file "NRC-VAD-Lexicon.txt" found within the zip that's downloadable from https://saifmohammad.com/WebDocs/Lexicons/NRC-VAD-Lexicon.zip. It's created by Dr. Saif M. Mohammad and Dr. Peter Turney at the National Research Council Canada. For questions contact the sentiment dictionary's author at saif.mohammad@nrc-cnrc.gc.ca (no affiliation with this repo).



### Run Tests After Any Change

Once you've made changes to configurable resources or more, you should run tests. Running tests after making any changes to the codebase is crucial for maintaining the integrity and stability of the Android app. Here’s why:

- **Catch Bugs Early:** Automated tests help identify issues immediately after changes are made, preventing bugs from creeping into production.
- **Ensure Functionality:** Tests verify that both new and existing features work as expected, ensuring that new code doesn't break anything.
- **Improve Code Quality:** Regular testing promotes better coding practices and helps maintain a high standard of code quality.
- **Facilitate Refactoring:** With comprehensive tests in place, you can refactor your code confidently, knowing that your tests will catch any mistakes.

### How to Execute Tests

To run tests, follow these steps:

1. **Using Android Studio:**
   - **Unit Tests:**
      - Go to the **Project** view and expand the `src/test/java` directory.
      - Right-click on the test class or method you want to run.
      - Select **Run 'testName()'** or **Run 'All Tests'** to execute all tests.
   - **Instrumented Tests:**
      - Expand the `src/androidTest/java` directory for UI and instrumented tests.
      - Right-click on the test class or method.
      - Select **Run 'testName()'** or **Run 'All Tests'**.

2. **Using the Command Line:**
   - **Unit Tests:**
      - Navigate to your project’s root directory.
      - Run the following command to execute all unit tests:
        ```bash
        ./gradlew test
        ```
   - **Instrumented Tests:**
      - To run all instrumented tests on a connected device or emulator, use:
        ```bash
        ./gradlew connectedAndroidTest
        ```

3. **Review Test Results:**
   - After the tests complete, review the results in the **Run** or **Test** tab in Android Studio.
   - If using the command line, results will be displayed in the terminal, and detailed reports can be found in the `build/reports/` directory.

Regularly running and reviewing your tests ensures that your app remains reliable and performant as you continue to develop and refine it.


## How to Cite the Screenlake Research Kit

Cornelius, Justin & Muise, Daniel (2025). Screenlake Research Kit, maintained by the Accelerator at Princeton University \[Software\]. GitHub. |add link to repo branch|

