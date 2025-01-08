# Tutorial: Creating a Cognito User Pool with Email Required and an App Client with Specific Authentication Flows

In this tutorial, we'll walk through the steps to create an AWS Cognito User Pool with email as a required attribute, and configure an App Client that supports specific authentication flows including custom authentication, refresh tokens, user password authentication, and SRP authentication. Throughout, 'user' refers to a research participants, i.e. a user of the compiled Android application. Once setup up properly, you'll have a system that allows participants to securely link up to storage buckets in your AWS backend just by entering credentials through the app on their smartphones.

## Prerequisites
- An AWS account with access to the AWS Management Console.
- Basic knowledge of AWS Cognito.

## Step 1: Create a Cognito User Pool

1. **Sign in to the AWS Management Console**:
   - Go to the [Cognito Console](https://console.aws.amazon.com/cognito/).

2. **Create a User Pool**:
   - Click on **"Manage User Pools"**.
   - Click **"Create a user pool"**.

3. **Configure Pool Name**:
   - Enter a name for your user pool (e.g., `MyUserPool`).
   - Click **"Review defaults"** to skip to the settings.

4. **Configure Sign-in Options**:
   - Under **How do you want your end users to sign in?**, select **"Email"** as a required sign-in option.
   - This ensures users will sign in using their email addresses.

5. **Configure Attributes**:
   - By default, email is a required attribute.
   - Ensure **email** is checked as a required attribute.

6. **Review and Create User Pool**:
   - Scroll down and click **"Create pool"**.
   - After creation, you will be directed to the user pool details page.

## Step 2: Create an App Client with Specific Authentication Flows

1. **Add an App Client**:
   - In the left navigation pane, under **App integration**, click **"App client"**.
   - Click **"Add an app client"**.

2. **App Client Settings**:
   - Enter an **App client name** (e.g., `MyAppClient`).
   - **Uncheck** the box that says **"Generate client secret"** since we want an app client with no secret.
   - Ensure that **"Enable sign-in API for server-based authentication (ADMIN_NO_SRP_AUTH)"** is checked.

3. **Set Authentication Flows**:
   - In the **Authentication flow configuration** section, select the following:
      - **ALLOW_CUSTOM_AUTH**: Allows custom authentication challenges.
      - **ALLOW_REFRESH_TOKEN_AUTH**: Allows users to refresh tokens.
      - **ALLOW_USER_PASSWORD_AUTH**: Allows users to authenticate using a username and password.
      - **ALLOW_USER_SRP_AUTH**: Allows Secure Remote Password (SRP) authentication.

4. **Create the App Client**:
   - Scroll down and click **"Create app client"**.
   - Your app client will now appear in the list of app clients.

## Step 3: Finalize Configuration

1. **Review App Client Settings**:
   - Go back to the **App clients** section under **App integration** and select your newly created app client.
   - Verify that the authentication flows you selected are enabled.

2. **Review User Pool Settings**:
   - Check the settings under **Attributes** and **Policies** to ensure email is required, and other configurations are as expected.

3. **Save Changes**:
   - If you made any changes, be sure to save them.