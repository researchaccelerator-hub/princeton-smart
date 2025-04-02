# AWS S3 Data Processing Script

This script automates the process of downloading, processing, and combining data from AWS S3 buckets. It is designed to filter files based on a date range, process zipped data, detect and redact faces in images, and consolidate CSV files.


---


When participants' data lands in the cloud, it's in zipped-format. Use the python script in the **scripts** folder to unpack and organize all of that data.

NOTES:
- Whereever you run the script from, participant data be be downloaded to temporarily. So, make sure that you run that script from an approved machine which participant data can safely be stored. An AWS ec2 is likely your best option, as it falls under the same security and institutional approval umbrella as the AWS cloud storage you're using.
- The script can run for a long time if you've got a lot of data collected and process it all at once. The script will stop/hang if its connection is interrupted, (e.g.) if your laptop falls-asleep midway. You can mitigate this by:
    - Running processing in date-bounded batches (super easy thanks to the script)
    - Making sure that there are no internet disruptions during the script's run
    - Choosing to use an ec2 versus a local laptop, as the laptop is more prone to interruptions
- The script is idempotent, meaning that even it gets stuck or hung up, you can just cancel the run midway and re-run it with no harm done and no duplicated data in your output.

[Watch a visual valkthrough here.](https://youtu.be/oFH0MieGgUY).


To understand the final output data, read through the explainer doc.
[Screenlake Research Kit: A Practical Guide](https://docs.google.com/document/d/1TMU9V169so5C-JHJscVokS_iKi2IjCiabze07KnQ41E/edit?usp=sharing)



---


## Features
1. **AWS S3 Integration**:
    - Connects to AWS S3 and lists files and folders within specified paths.
    - Filters objects by a user-defined date range.

2. **Batch Processing**:
    - Downloads files in batches using multiprocessing for efficiency.
    - Supports large-scale data processing workflows.

3. **Unzipping and Processing**:
    - Extracts ZIP files, processes their contents, and handles images by detecting and redacting faces.

4. **CSV Consolidation**:
    - Combines multiple CSV files grouped by specific prefixes into consolidated CSV files.

5. **Cleanup**:
    - Deletes intermediate directories and files after processing to save space.

---

## Requirements
- **Python**: Ensure Python 3.x is installed.
- **Dependencies**: Install the required Python packages by running:
  ```bash
  pip install boto3 pandas tqdm opencv-python pytz
  ```

---

## How to Use

### 1. **Set Up AWS Credentials**
Ensure your AWS credentials are configured. You can either:
- Use the `~/.aws/credentials` file.
- Provide AWS Access Key and Secret Key when prompted.

### 2. **Run the Script**
Execute the script using Python:
```bash
python script_name.py
```

### 3. **Follow the Prompts**
The script will guide you through:
- Selecting an S3 bucket and path.
- Specifying a date range for filtering objects.
- Setting batch sizes and processor count.

### 4. **Outputs**
- Extracted data is saved in a directory named after a unique query ID.
- Consolidated CSV files are saved in `<query_id>/combined/panelists/<folder>/metadata`.

---

## Configuration Options
- **AWS S3 Bucket Name**: Default is `screenlake-zip-prod`. You can change it when prompted.
- **Date Range**: Specify the start and end dates (defaults are 1 year ago and today).
- **Batch Size**: Number of files to download in a single batch (default: 25).
- **Processors**: Number of parallel processes to use (default: 4).

---

## File Structure
After processing, the following structure is created:
```
<query_id>/
├── combined/
│   └── panelists/
│       └── <folder>/
│           ├── metadata/
│           │   ├── screenshot_data-consolidated.csv
│           │   ├── app_accessibility_data-consolidated.csv
│           │   └── ...
│           └── images/
├── zipped/
└── unzipped/
```

---

## Face Detection and Redaction
- **Face Detection**: Detects faces in images using OpenCV's pre-trained Haar cascades.
- **Redaction**: Faces can be blurred or redacted (default is blacking out the faces).

---

## Cleaning Up
Temporary files and folders are automatically deleted after processing. The consolidated results remain in the `<query_id>/combined` folder.

---

## Limitations
- Requires valid AWS credentials.
- Ensure sufficient storage for large datasets during processing.

---

## Troubleshooting
- **Missing AWS Credentials**: Ensure `~/.aws/credentials` is properly configured or provide credentials when prompted.
- **Large Files**: Adjust batch sizes and processor count for better performance on large datasets.

---

## Example Usage
1. **Start the Script**:
   ```bash
   python script_name.py
   ```
2. **Follow the Prompts**:
    - Select the S3 bucket and path.
    - Specify the date range and processing parameters.
3. **Output**:
    - Consolidated CSV files and processed images will be available in the `<query_id>/combined` directory.

---

## Dependencies
- **boto3**: AWS SDK for Python.
- **pandas**: For handling CSV files.
- **tqdm**: Progress bar for file downloads.
- **opencv-python**: For image processing and face detection.
- **pytz**: Timezone support.