# Tutorial: Creating a Cognito User Pool with Email Required and an App Client with Specific Authentication Flows

In this tutorial, we'll walk through the steps to create an AWS Cognito User Pool with email as a required attribute, and configure an App Client that supports specific authentication flows including custom authentication, refresh tokens, user password authentication, and SRP authentication. Throughout, 'user' refers to a research participants, i.e. a user of the compiled Android application. Once setup up properly, you'll have a system that allows participants to securely link up to storage buckets in your AWS backend just by entering credentials through the app on their smartphones.

## Prerequisites
- An AWS account with access to the AWS Management Console.
- Basic knowledge of AWS Cognito.

## Step 1: Create a Cognito User Pool

1. **Sign in to the AWS Management Console**:
    - Go to the [Cognito Console](https://console.aws.amazon.com/cognito/).

2. **Create a User Pool**:
    - Click **"Create a user pool"**.

3. **Define your application**:
    - Enter a name for your application (e.g., `My app - ID`).
    - For **Application Type**, click **Mobile App**.

4. **Configure Options**:
    - Under **Options for sign-in identifiers**, select **"Email"** as a required sign-in option.

5. **Finish User Pool Creation**:
    - Click the **Create User Directory** button, and then on the next page, scroll all the way down until you see the **Go to Overview**
    - Click **Go to Overview** to go the dashboard where you will see your new User Pool.
    - Note that the user pool isn’t named by the application name you entered. Rather, it's given a generic name by AWS, which might be inconvenient to find later. You'll want to go to the **Amazon Cognito** >> **User pools** page, look for the most-recently-created user pool (using sort by "created time"), and rename your new user pool.



## Step 2: Create an App Client with Specific Authentication Flows

1. **Add an App Client**:
    - In the left navigation pane, under **App integration**, click **"App client"**.
    - There should be an app client already created, select that app client and **click** the **edit** button in the top right hand corner and if you do not see an app client **click** **"Add an app client"**.

2. **Edit App Client Settings**:
    - Enter an **App client name** (e.g., `MyAppClient`).

3. **Set Authentication Flows**:
    - In the **Authentication flow configuration** section, select the following:
        - **ALLOW_CUSTOM_AUTH**: Allows custom authentication challenges.
        - **ALLOW_REFRESH_TOKEN_AUTH**: Allows users to refresh tokens.
        - **ALLOW_USER_PASSWORD_AUTH**: Allows users to authenticate using a username and password.
        - **ALLOW_USER_SRP_AUTH**: Allows Secure Remote Password (SRP) authentication.

![alt text](<images/user_pool_final_configs.png>)

4. **Finish the App Client Settings**:
    - Scroll down and click **"Save Changes"**.