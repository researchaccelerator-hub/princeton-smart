# AWS S3 Data Processing Script

This script automates the process of downloading, processing, and combining data from AWS S3 buckets. It is designed to filter files based on a date range, process zipped data, detect and redact faces in images, and consolidate CSV files.


---


When participants' data lands in the cloud, it's in zipped-format. Use the python script in the **scripts** folder to unpack and organize all of that data.

NOTES:
- Wherever you run the script from, participant data will be downloaded to that machine temporarily. So, make sure that you run that script from an approved machine where participant data can safely be stored. An AWS ec2 is likely your best option, as it falls under the same security and institutional approval umbrella as the AWS cloud storage you're using.
- The script can run for a long time if you've got a lot of data collected and process it all at once. The script will stop/hang if its connection is interrupted, (e.g.) if your laptop falls-asleep midway. You can mitigate this by:
    - Running processing in date-bounded batches (super easy thanks to the script)
    - Making sure that there are no internet disruptions during the script's run
    - Choosing to use an ec2 versus a local laptop, as the laptop is more prone to interruptions
- The script can be resumed. If it gets stuck or hung up, cancel it and re-run it with the same `--query-id` (the output folder it created). Files already downloaded are skipped and there is no duplicated data in your output.

[Watch a visual walkthrough here.](https://youtu.be/oFH0MieGgUY).


To understand the final output data, read through the explainer doc.
[Screenlake Research Kit: A Practical Guide](https://docs.google.com/document/d/1TMU9V169so5C-JHJscVokS_iKi2IjCiabze07KnQ41E/edit?usp=sharing)



---


## Features
1. **AWS S3 Integration**:
    - Connects to AWS S3 and lists files and folders within specified paths (including folders past the first 1,000 entries).
    - Filters objects by a user-defined date range. Dates are whole days in your computer's local time, and both the start and end day are included.
    - Downloads CSV ZIPs, image ZIPs, or both. Choosing CSV only avoids downloading images, which is most of the data volume.

2. **Batch Processing**:
    - Downloads files in batches using multiprocessing for efficiency.
    - Supports large-scale data processing workflows.

3. **Unzipping and Processing**:
    - Extracts ZIP files, processes their contents, and handles images by detecting and redacting faces.

4. **CSV Consolidation**:
    - Combines multiple CSV files grouped by specific prefixes into consolidated CSV files.
    - Matches columns by name, so data from different app versions lines up in one file.
    - Repairs rows where free text in the last column contains unquoted commas, instead of failing.
    - Optionally removes OCR and on-screen text columns (`text`, `ocr`, `ocr_text`) from the output.

5. **Daily Summaries**:
    - Writes screenshot and image counts per participant per day to `<query_id>/combined/summary/`.

6. **Cleanup**:
    - Deletes intermediate directories and files after processing to save space.

---

## Requirements
- **Python**: Ensure Python 3.x is installed.
- **Dependencies**: Install the required Python packages by running:
  ```bash
  pip install -r requirements.txt
  ```
  OpenCV must be version 4.x. OpenCV 5 removed the face detector used for redaction, and the script stops before downloading images if redaction is unavailable.

---

## How to Use

### 1. **Set Up AWS Credentials**
Ensure your AWS credentials are configured. You can either:
- Use the `~/.aws/credentials` file.
- Provide AWS Access Key and Secret Key when prompted.

### 2. **Run the Script**
Execute the script using Python:
```bash
python consolidatecsvs.py
```

Any option you leave out is asked for interactively. To skip the prompts:
```bash
python consolidatecsvs.py --aws-default-credentials --bucket my-study-bucket \
    --prefix academia/tenant/<tenant>/panel/<panel>/<version>/panelist/ \
    --start 2026-10-01 --end 2026-10-07 --zip-types csv --remove-ocr \
    --batch-size 25 --processors 4 --query-id query_oct_week1
```

### 3. **Follow the Prompts**
The script will guide you through:
- Selecting an S3 bucket and path.
- Specifying a date range for filtering objects.
- Choosing which ZIP files to download (CSV, images, or both).
- Choosing whether to remove OCR text columns (only asked when CSVs are downloaded).
- Setting batch sizes and processor count.

### 4. **Outputs**
- Extracted data is saved in a directory named after the query ID (`query_<date>_<time>` unless you pass `--query-id`).
- Consolidated CSV files are saved in `<query_id>/combined/panelists/<folder>/metadata`.

---

## Configuration Options
| Option | Prompt default | Description |
|---|---|---|
| `--bucket` | `$SMART_S3_BUCKET`, else `screenlake-zip-prod` | S3 bucket your study uploads to. Set `SMART_S3_BUCKET` to change the default for your institution. |
| `--start`, `--end` | 1 year ago, today | Inclusive date range, `YYYY-MM-DD`, local time. |
| `--zip-types` | `csv` | `csv`, `image`, or `both`. |
| `--remove-ocr` / `--keep-ocr` | keep | Drop or keep `text`, `ocr`, and `ocr_text` columns in the consolidated CSVs. Applies to any ZIP type, since pre-1.50 image ZIPs contain CSVs. |
| `--batch-size` | 25 | Number of files to download in a single batch. |
| `--processors` | 4 | Number of parallel download processes. |
| `--aws-default-credentials` | ask | Use your AWS profile, environment variables, or EC2 instance role without asking. |
| `--prefix` | folder selector | S3 path to download (ends in `panelist/`), skipping the selector. |
| `--query-id` | `query_<date>_<time>` | Output folder name. Reuse a folder name to resume an interrupted run. |

### ZIP file names
The app uploads CSVs and images in separate ZIPs: `csv_zip_<id>.zip` and `image_zip_<id>_<count>.zip`. The script also accepts `csv_zip_<id>_<count>.zip`, the name used by the University of Minnesota build.

App versions before 1.50 uploaded a single `image_zip_` file containing both CSVs and images. For data in those version folders, choose `both` (or `image`); CSVs found in those ZIPs are still consolidated.

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
│           │   ├── app_segment_data-consolidated.csv
│           │   ├── session_data-consolidated.csv
│           │   ├── package_event_data-consolidated.csv
│           │   └── manifest-consolidated.csv
│           └── images/          (face-redacted copies only)
│   └── summary/
│       ├── images_per_participant_day.csv
│       ├── images_per_day.csv
│       ├── participant_statistics.csv
│       └── overall_statistics.csv
├── zipped/
└── unzipped/
```

---

## Face Detection and Redaction
- **Face Detection**: Detects faces in images using OpenCV's pre-trained Haar cascades.
- **Redaction**: Faces can be blurred or redacted (default is blacking out the faces).
- Only redacted copies of images are kept in the output. An image that cannot be read is logged and left out, never copied unredacted.

### State summaries
If the screenshot data has a `state` column (SMART 1.53.0 and later, and University of Minnesota builds), the summary folder also gets per-state counts and `participants_without_normal_3_consecutive_days.csv`, which flags participants with 3 or more consecutive days without any `Normal` screenshot.

`screenshot_count` in the summaries counts every screenshot row, including `Restricted` and state marker rows. `image_count` counts distinct file names in the screenshot data; for data without a `state` column (before SMART 1.53.0), a day with no file names counts every row as an image. With state data, use the `Normal` counts for captured screenshots.

---

## Cost Estimation
`cost_estimation.py` estimates monthly S3 cost for a study and compares downloading combined ZIPs with downloading only the CSV ZIPs:
```bash
python cost_estimation.py --participants 150 --images-per-day 4000 --image-mb 0.5
```
Run `python cost_estimation.py --help` for all inputs. Rates default to S3 Standard list prices; check current AWS pricing before relying on the result.

---

## Tests
```bash
pip install -r requirements.txt -r tests/requirements.txt
python -m pytest tests
```

---

## Developer tools

`dev/test_recording_interruption.sh` checks on a test phone that the app notices when Android closes it while recording. Start recording in a debug build, connect the phone with `adb`, then run one scenario:

```bash
scripts/dev/test_recording_interruption.sh kill       # like a low-memory kill; expect "Study Recording Stopped"
scripts/dev/test_recording_interruption.sh crash      # Android 11+
scripts/dev/test_recording_interruption.sh trim       # memory pressure only; recording should keep running
scripts/dev/test_recording_interruption.sh forcestop  # expect no notification: force stop cancels it
scripts/dev/test_recording_interruption.sh status
```

Set `PKG=com.screenlake` to test a release build (the `kill` scenario then needs a rooted phone).

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
   python consolidatecsvs.py
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
- **opencv-python** (4.x): For image processing and face detection.