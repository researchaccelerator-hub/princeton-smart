# Tutorial: Creating an S3 Bucket with Blocked Public Access and Enabled Encryption

This tutorial will guide you through the steps to create an Amazon S3 bucket with all public access blocked and server-side encryption enabled. An S3 bucket is a storage bucket for data. Data collected from participants will leave their smartphones and go directly to the S3 bucket you create and designate. It's important to keep access to this S3 bucket properly locked-down and exclusive. Luckily, you can just follow these steps.

## Prerequisites
- An AWS account with access to the AWS Management Console.

## Step 1: Create an S3 Bucket

1. **Sign in to the AWS Management Console**:
    - Go to the [S3 Console](https://console.aws.amazon.com/s3/).

2. **Create a New Bucket**:
    - Click on **"Create bucket"**.
    - Enter a unique **Bucket name** (e.g., `my-secure-bucket`).
    - Choose the **AWS Region** where you want the bucket to reside.

3. **Block Public Access Settings for this Bucket**:
    - Ensure that the **Block all public access** setting is enabled. This should be checked by default.
    - The following options should be checked:
        - **Block all public access**
        - **Block public access to buckets and objects granted through new access control lists (ACLs)**
        - **Block public access to buckets and objects granted through any access control lists (ACLs)**
        - **Block public access to buckets and objects granted through new public bucket or access point policies**
        - **Block public and cross-account access to buckets and objects through any public bucket or access point policies**

4. **Enable Bucket Versioning (Optional)**:
    - If you want to keep multiple versions of objects in the bucket, you can enable versioning. This is optional but recommended for additional data protection.

5. **Enable Default Encryption**:
    - Scroll down to the **Default encryption** section.
    - Select **"Enable"** under "Bucket encryption".
    - Choose the encryption type:
        - **SSE-S3**: Server-side encryption with S3 managed keys.
        - **SSE-KMS**: Server-side encryption with AWS KMS keys (recommended for more control).
    - If you choose **SSE-KMS**, select or create a KMS key for encryption.

6. **Review and Create Bucket**:
    - Review all the settings.
    - Click on **"Create bucket"**.

## Step 2: Verify Bucket Settings

1. **Verify Public Access Settings**:
    - Navigate to your new bucket and click on the **"Permissions"** tab.
    - Under **"Block public access (bucket settings)"**, verify that all public access is blocked.

2. **Verify Encryption Settings**:
    - In the **"Properties"** tab, scroll down to **"Default encryption"** and confirm that encryption is enabled with your selected method (SSE-S3 or SSE-KMS).

## Conclusion

You have successfully created an Amazon S3 bucket with all public access blocked and server-side encryption enabled. This ensures that your data is securely stored and inaccessible to the public by default. You can now use this bucket to securely store your data.