# Setting Up an Authenticated Identity with Cognito and Attaching an S3 Policy

This guide explains how to configure an **Amazon Cognito Identity Pool** for **authenticated identities** and attach a policy that grants access to an S3 bucket. Once setup up properly, you'll have a system that allows participants to securely link up to storage buckets in your AWS backend just by entering credentials through the app on their smartphones.

---

## Prerequisites
1. **AWS Account**: Ensure you have access to an AWS account.
2. **User Pool**: A Cognito User Pool is already created.
3. **IAM Permissions**: You need permissions to manage Cognito, IAM, and S3 resources.

---

## Steps

### 1. **Create a Cognito Identity Pool**
1. **Navigate to Amazon Cognito**:
   - Go to the **AWS Management Console**.
   - Navigate to **Amazon Cognito** > **Identity Pools**.

2. **Create a New Identity Pool**:
   - Click **Create Identity Pool**.
   - Under **User access** select **Authenticated access**.
   - Under **Authenticated identity sources**, select **Amazon Cognito user pool** and click **next**:
   - Create a new **IAM role name** and click **next**.
      - Find your **User Pool ID** and **App Client ID** you just made in the dropdown:
         - **User Pool ID**: Found in the Cognito User Pool dashboard.
         - **App Client ID**: Found under the User Pool > App clients.
   - Click **next**.
   - Name your and then click **next** and then **create identity pool**.

3. **Click Create Pool**.

---

### 2. **Configure IAM Roles for Authenticated Identities**
During the setup, Cognito creates two default IAM roles:
- **Authenticated Role**: Used for authenticated users (connected to the User Pool).
- **Unauthenticated Role**: Used for guest users (not used in this setup).

1. From the Identity Pools Dashboard, select the identity pool you just created.
2. On the **User Access** tab, Select the **Authenticated Role** name (e.g., `Cognito_MyAppAuth_Role`).
3. This will take you to the role, from here, you will configure a policy to allow users to take certain actions.

---

### 3. **Attach a Custom S3 Policy to the Authenticated Role**
1. Select **Add permissions** >  **Create inline policy**.
2. Choose the **JSON** editor and paste the following policy into the editor box, overwriting the existing boilerplate contents:

```json
{
   "Version": "2012-10-17",
   "Statement": [
      {
         "Effect": "Allow",
         "Action": "s3:PutObject",
         "Resource": [
            "arn:aws:s3:::<BUCKET-NAME>/PATH/TO/UPLOAD",
            "arn:aws:s3:::<BUCKET-NAME>/PATH/TO/UPLOAD/*"
         ]
      }
   ]
}
```
3. Replace /PATH/TO/UPLOAD with the AWS file location where app users’ screenshots and data will go. This should be the s3 you just created (if you’re following this documentation in order). When you replace the path, be sure to keep the formatting as it is in this pasting. For example, if the destination is just the top-level s3 bucket (that’s fine) then the policy should look something like this:

```json
{
   "Version": "2012-10-17",
   "Statement": [
      {
         "Effect": "Allow",
         "Action": "s3:PutObject",
         "Resource": [
            "arn:aws:s3:::srk-dan-test-2-bucket",
            "arn:aws:s3:::srk-dan-test-2-bucket/*"
         ]
      }
   ]
}
```

4. Review what you’ve done, click next, name the policy, and then click “Create Policy”.

---

## Summary
You’ve successfully:
1. Created an Identity Pool for **authenticated identities** connected to your User Pool.
2. Configured IAM roles for authenticated users.
3. Attached a custom S3 policy to allow authenticated users to upload files with specific prefixes.

This setup ensures that authenticated users can securely interact with your S3 bucket under controlled permissions. Let me know if you have further questions!
