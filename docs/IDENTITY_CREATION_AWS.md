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
    - Navigate to **Amazon Cognito** > **Manage Identity Pools**.

2. **Create a New Identity Pool**:
    - Click **Create New Identity Pool**.
    - Name the pool (e.g., `MyAppAuthIdentityPool`).
    - Under **Authentication Providers**, select **Cognito**:
        - Enter your **User Pool ID** and **App Client ID**:
            - **User Pool ID**: Found in the Cognito User Pool dashboard.
            - **App Client ID**: Found under the User Pool > App clients.

3. **Click Create Pool**.

---

### 2. **Configure IAM Roles for Authenticated Identities**
During the setup, Cognito creates two default IAM roles:
- **Authenticated Role**: Used for authenticated users (connected to the User Pool).
- **Unauthenticated Role**: Used for guest users (not used in this setup).

1. On the **IAM Roles** screen, note the **Authenticated Role** name (e.g., `Cognito_MyAppAuth_Role`).
2. Select **Allow** to complete the creation of the Identity Pool.

---

### 3. **Attach a Custom S3 Policy to the Authenticated Role**
1. Go to **IAM** > **Roles**.
2. Locate the **Authenticated Role** created by Cognito (e.g., `Cognito_MyAppAuth_Role`).
3. Click on the role to open its details.
4. Select **Add permissions** > **Attach policies** > **Create inline policy**.
5. Choose the **JSON** editor and paste the following policy:

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
/PATH/TO/UPLOAD is the location of user files in the S3 bucket.

6. Review and save the policy.

---

## Summary
You’ve successfully:
1. Created an Identity Pool for **authenticated identities** connected to your User Pool.
2. Configured IAM roles for authenticated users.
3. Attached a custom S3 policy to allow authenticated users to upload files with specific prefixes.

This setup ensures that authenticated users can securely interact with your S3 bucket under controlled permissions. Let me know if you have further questions!
