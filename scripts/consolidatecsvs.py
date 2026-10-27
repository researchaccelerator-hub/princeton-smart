import argparse
import csv
import json
import logging
import multiprocessing
import os
import pathlib
import shutil
import stat
import sys
import zipfile
from datetime import datetime, timedelta, timezone

import pandas as pd

try:
    from tqdm import tqdm
except ModuleNotFoundError:
    def tqdm(iterable=None, *args, total=None, **kwargs):
        return iterable if iterable is not None else range(total or 0)

try:
    import cv2
except ModuleNotFoundError:
    cv2 = None

DEFAULT_BUCKET_NAME = "screenlake-zip-prod"
BUCKET_ENV_VAR = "SMART_S3_BUCKET"

# The app uploads CSVs and images in separate ZIPs. SMART names them csv_zip_<id>.zip and
# image_zip_<id>_<count>.zip; UMN builds use csv_zip_<id>_<count>.zip. Matching on the
# prefix accepts both. Uploads from app versions before the split (SMART 1.50) are a single
# image_zip_ file holding both CSVs and images, so they need the "image" or "both" choice.
ZIP_TYPE_PREFIXES = {
    "csv": "csv_zip_",
    "image": "image_zip_"
}

ZIP_TYPE_CHOICES = {
    "csv": {"csv"},
    "image": {"image"},
    "both": {"csv", "image"}
}

CSV_PREFIXES = [
    "screenshot_data",
    "app_accessibility_data",
    "app_segment_data",
    "session_data",
    "package_event_data",
    "manifest"
]

# Free-text screen content: OCR text in screenshot_data and on-screen text in
# app_accessibility_data.
OCR_COLUMN_NAMES = {"text", "ocr", "ocr_text"}

IMAGE_EXTENSIONS = ('.jpg', '.jpeg', '.png')

s3 = None
face_cascade = None


def get_s3_client(*args, **kwargs):
    """
    Creates an S3 client lazily so the local helpers can run without boto3.
    """
    import boto3
    return boto3.client('s3', *args, **kwargs)


def read_downloaded_csv(file_path):
    """Read a downloaded CSV, repairing unquoted commas in its final column.

    Some uploads contain a comma-containing value (usually accessibility ``text``) without
    CSV quoting. Pandas rejects those rows because they have more fields than the header.
    The free-text column is last in every schema, so on a parser error the surplus fields
    are joined back into that final value instead of dropping data.
    """
    try:
        return pd.read_csv(file_path)
    except pd.errors.ParserError as error:
        logging.warning(
            "Malformed CSV detected in %s; repairing unquoted commas in the "
            "final column. Original error: %s",
            file_path,
            error,
        )

    with open(file_path, "r", encoding="utf-8-sig", newline="", errors="replace") as csv_file:
        reader = csv.reader(csv_file)
        try:
            header = next(reader)
        except StopIteration:
            raise pd.errors.EmptyDataError(f"No columns to parse from file: {file_path}")

        expected_fields = len(header)
        rows = []
        repaired_rows = 0
        for line_number, row in enumerate(reader, start=2):
            if len(row) > expected_fields:
                row = row[:expected_fields - 1] + [",".join(row[expected_fields - 1:])]
                repaired_rows += 1
            elif len(row) < expected_fields:
                logging.warning(
                    "CSV row %d in %s has %d fields; padding it to %d fields.",
                    line_number,
                    file_path,
                    len(row),
                    expected_fields,
                )
                row += [""] * (expected_fields - len(row))
            rows.append(row)

    if repaired_rows:
        logging.warning("Repaired %d malformed row(s) in %s.", repaired_rows, file_path)
    return pd.DataFrame(rows, columns=header)


def read_csv_header(file_path):
    """Returns the column names from the first line of a CSV file."""
    with open(file_path, "r", encoding="utf-8-sig", newline="", errors="replace") as csv_file:
        return next(csv.reader(csv_file), [])


def parse_args(argv=None):
    """
    Parses command-line options. Any option left out is asked for interactively.
    """
    parser = argparse.ArgumentParser(
        description="Download participant ZIPs from S3, redact faces, and consolidate CSVs."
    )
    parser.add_argument("--bucket", help=f"S3 bucket (default: ${BUCKET_ENV_VAR} or {DEFAULT_BUCKET_NAME})")
    parser.add_argument("--start", help="First day to include, YYYY-MM-DD, local time")
    parser.add_argument("--end", help="Last day to include, YYYY-MM-DD, local time")
    parser.add_argument("--zip-types", choices=sorted(ZIP_TYPE_CHOICES), help="ZIP files to download")
    ocr = parser.add_mutually_exclusive_group()
    ocr.add_argument("--remove-ocr", dest="remove_ocr", action="store_true", default=None,
                     help="Drop OCR/on-screen text columns from consolidated CSVs")
    ocr.add_argument("--keep-ocr", dest="remove_ocr", action="store_false",
                     help="Keep OCR/on-screen text columns")
    parser.add_argument("--batch-size", type=int, help="ZIP files per download batch (default: 25)")
    parser.add_argument("--processors", type=int, help="Parallel download processes (default: 4)")
    parser.add_argument("--query-id", help="Output folder name (default: query_<timestamp>)")
    return parser.parse_args(argv)


def get_download_file_types_from_user():
    """
    Prompts the user to choose which S3 ZIP file types should be downloaded.

    Returns:
        set: One or both of {"csv", "image"}.
    """
    choices = {
        "1": {"csv"},
        "csv": {"csv"},
        "c": {"csv"},
        "2": {"image"},
        "image": {"image"},
        "images": {"image"},
        "i": {"image"},
        "3": {"csv", "image"},
        "both": {"csv", "image"},
        "all": {"csv", "image"},
        "b": {"csv", "image"}
    }

    while True:
        print("Choose ZIP file types to download:")
        print("1. CSV only")
        print("2. Images only")
        print("3. Both CSV and images (needed for uploads from app versions before 1.50)")
        choice = input("Enter your choice (default: CSV only): ").strip().lower() or "csv"

        if choice in choices:
            return choices[choice]

        print("Invalid choice. Enter csv, image, or both.")


def should_remove_ocr_from_consolidated_csvs():
    """Ask whether OCR text should be excluded from consolidated CSV outputs."""
    while True:
        choice = input(
            "Remove OCR text columns from consolidated CSVs? (y/N): "
        ).strip().lower()
        if choice in {"", "n", "no"}:
            return False
        if choice in {"y", "yes"}:
            return True
        print("Invalid choice. Enter y or n.")


def s3_object_matches_download_type(obj, download_file_types):
    """
    Returns True when an S3 object is one of the selected ZIP types.
    """
    filename = os.path.basename(obj["Key"]).lower()
    if not filename.endswith(".zip"):
        return False

    return any(
        filename.startswith(ZIP_TYPE_PREFIXES[file_type])
        for file_type in download_file_types
    )


# Step 1: Connect to AWS S3
def connect_to_s3():
    """
    Connects to AWS S3 using existing credentials or prompts for new credentials.
    Returns:
        boto3 S3 client
    """
    credentials_path = os.path.expanduser("~/.aws/credentials")
    config_path = os.path.expanduser("~/.aws/config")

    # Check if AWS credentials and configuration files exist
    if os.path.exists(credentials_path) and os.path.exists(config_path):
        use_existing = input("AWS credentials are found. Use existing credentials? (y/n): ")
        if use_existing.lower() == 'y' or not use_existing:
            logging.info("Using existing AWS credentials.")
            return get_s3_client()

    aws_access_key = input("Enter AWS Access Key: ")
    aws_secret_key = input("Enter AWS Secret Key: ")
    # Download workers run in separate processes and create their own clients, so share
    # entered keys through the environment they inherit.
    os.environ["AWS_ACCESS_KEY_ID"] = aws_access_key
    os.environ["AWS_SECRET_ACCESS_KEY"] = aws_secret_key
    return get_s3_client(aws_access_key_id=aws_access_key, aws_secret_access_key=aws_secret_key)


def parse_date_range(start_date_str, end_date_str, now=None):
    """
    Parses YYYY-MM-DD strings into an inclusive local-time range. Empty strings default to
    one year ago and today.
    """
    now = now or datetime.now()
    start_date = datetime.strptime(start_date_str, "%Y-%m-%d") if start_date_str else now - timedelta(days=365)
    end_date = datetime.strptime(end_date_str, "%Y-%m-%d") if end_date_str else now

    start_date, end_date = normalize_date_range(start_date, end_date)

    if start_date > end_date:
        raise ValueError("Start date cannot be after end date.")
    return start_date, end_date


def get_date_range_from_user():
    """
    Prompts the user to input a date range and returns the start and end dates as datetime objects.
    Returns:
        tuple: start_date, end_date
    """
    start_date_str = input("Enter the start date (YYYY-MM-DD, or press Enter for 1 year ago): ")
    end_date_str = input("Enter the end date (YYYY-MM-DD, or press Enter for today): ")
    return parse_date_range(start_date_str, end_date_str)


def normalize_date_range(start_date, end_date):
    """
    Treat user-entered dates as inclusive whole calendar days.
    """
    start_date = start_date.replace(hour=0, minute=0, second=0, microsecond=0)
    end_date = end_date.replace(hour=23, minute=59, second=59, microsecond=999999)
    return start_date, end_date


def as_utc_datetime(value):
    """
    Converts a datetime to a timezone-aware UTC datetime.

    Dates entered at the prompt have no timezone and represent the computer's local time.
    Treating them as UTC shifts the requested window by the local UTC offset, which can
    omit recently uploaded ZIP files.
    """
    if value.tzinfo is None:
        value = value.astimezone()

    return value.astimezone(timezone.utc)


def list_folders_in_path(s3, bucket_name, path):
    """
    Lists folders in a specified path within an S3 bucket.
    Args:
        s3: Boto3 S3 client
        bucket_name: Name of the S3 bucket
        path: Path within the S3 bucket
    Returns:
        list: Folders under the specified path
    """
    # S3 returns at most 1,000 entries per ListObjectsV2 call. Follow continuation tokens
    # so folders on later pages remain selectable.
    folders = []
    continuation_token = None

    while True:
        params = {
            "Bucket": bucket_name,
            "Prefix": path,
            "Delimiter": "/"
        }
        if continuation_token:
            params["ContinuationToken"] = continuation_token

        response = s3.list_objects_v2(**params)
        folders.extend(
            prefix["Prefix"]
            for prefix in response.get("CommonPrefixes", [])
            if prefix.get("Prefix")
        )

        continuation_token = response.get("NextContinuationToken")
        if not response.get("IsTruncated") or not continuation_token:
            break

    return folders


def get_bucket_name(bucket_arg=None):
    """
    Returns the bucket from the command line, the environment, or a prompt.
    """
    if bucket_arg:
        return bucket_arg

    default_bucket_name = os.environ.get(BUCKET_ENV_VAR) or DEFAULT_BUCKET_NAME
    return input(f"Enter the S3 bucket name (default: {default_bucket_name}): ") or default_bucket_name


def get_bucket_and_path(s3, bucket_name):
    """
    Interactively select a directory path within an AWS S3 bucket based on user input.

    Returns:
        Tuple containing the selected bucket name and directory path.
    """
    # Start with the base path and list folders
    base_path = "academia/tenant/"
    selected_path = base_path
    header = 'tenant'
    prevHeader = ''
    seen_version = False
    while True:
        folders = list_folders_in_path(s3, bucket_name, selected_path)
        if len(folders) >= 1:
            if folders[len(folders) - 1].endswith("panel/"):
                header = 'panel'
                selected_path = folders[len(folders) - 1]
                continue

            if "V_" in folders[len(folders) - 1] and not seen_version:
                seen_version = True
                header = 'version'

            if prevHeader == 'panelist':
                header = 'Here is a list of panelist who will have CSVs created'

        prevHeader = header
        if not folders:
            raise ValueError(f"No folders found at '{selected_path}'")

        if folders[len(folders) - 1].endswith("panelist/"):
            return bucket_name, selected_path
        else:
            # Display folder options for user selection
            logging.info(f'Select a {header}:')
            for i, folder in enumerate(folders, start=1):
                split_folders = folder.split('/')
                folder_name = split_folders[len(split_folders) - 2]
                print(f"{i}. {folder_name}")

            choice = input("Enter the number corresponding to your choice (q to quit): ")

        if choice.lower() == 'q':
            break

        try:
            if not choice:
                choice = 1
            choice = int(choice)
            if 1 <= choice <= len(folders):
                selected_folder = folders[choice - 1]
                selected_path = selected_folder
            else:
                logging.info("Invalid choice. Please enter a valid number.")
        except ValueError:
            logging.info("Invalid input. Please enter a number or 'q' to quit.")

    return bucket_name, selected_path


# Step 4: Set batch and processor parameters
def set_batch_and_processors(query_id, download_file_types, remove_ocr_columns=False,
                             zip_batch_size=None, num_processors=None):
    """
    Sets up the batch size for zip file downloads and the number of processors to use.
    Also prepares the directory structure for storing query results.

    Returns:
        A tuple containing the batch size and number of processors.
    """
    # Prompt the user for batch size and number of processors with default values
    try:
        if zip_batch_size is None:
            zip_batch_size = int(input("Enter the batch size for zip file downloads (default: 25): ") or 25)
        if num_processors is None:
            num_processors = int(input("Enter the number of processors (default: 4): ") or 4)
    except ValueError as e:
        logging.error("Invalid input, please enter a number.")
        raise e

    # Configuration dictionary for the query
    query_config = {
        "queryId": query_id,
        "batchSize": zip_batch_size,
        "downloadFileTypes": sorted(download_file_types),
        "removeOcrColumns": remove_ocr_columns,
        "numFilesToDownload": 0,  # Initial setup, no files to download yet
        "numFilesDownloaded": 0,  # Tracker for downloaded files
        "lastBatchBeginId": ""  # Placeholder for tracking batches
    }

    # Prepare directories for zipped and unzipped files
    directories = {
        "base": query_id,
        "zipped": os.path.join(query_id, "zipped"),
        "unzipped": os.path.join(query_id, "unzipped"),
        "combined": os.path.join(query_id, "combined")
    }

    # Create the directories if they don't exist
    for directory in directories.values():
        os.makedirs(directory, exist_ok=True)

    # Save the query configuration to a JSON file
    config_path = os.path.join(query_id, "query_config.json")
    with open(config_path, "w") as config_file:
        json.dump(query_config, config_file)

    logging.info(f"Configuration saved to {config_path}.")
    return zip_batch_size, num_processors


def run_in_processes(target, args_list, max_processes, on_done=None):
    """
    Runs target once per args tuple, with at most max_processes running at a time.

    Every args tuple is started exactly once, and all processes have finished when this
    returns.
    """
    processes = []
    for args in args_list:
        if len(processes) >= max_processes:
            for process in processes:
                process.join()
                if on_done:
                    on_done()
            processes = []

        process = multiprocessing.Process(target=target, args=args)
        process.start()
        processes.append(process)

    for process in processes:
        process.join()
        if on_done:
            on_done()


# Step 5: Download zip files in batches
def download_zip_files_in_batches(s3, bucket_name, path, start_date, end_date, zip_batch_size, num_processors,
                                  query_id, download_file_types):
    """
     Downloads objects from S3 in batches, using multiple processes.

     Args:
         s3: Boto3 S3 client instance.
         bucket_name: Name of the S3 bucket.
         path: Path within the S3 bucket from which to download objects.
         start_date: Start date for querying objects.
         end_date: End date for querying objects.
         zip_batch_size: Number of files to download in each batch.
         num_processors: Number of parallel processes to use for downloading.
         query_id: Unique identifier for the query/download session.
         download_file_types: Set containing "csv", "image", or both.
     """
    objects_to_download = query_s3_objects_in_date_range(
        s3,
        bucket_name,
        path,
        start_date,
        end_date,
        download_file_types
    )
    num_objects = len(objects_to_download)

    if num_objects == 0:
        logging.warning("No matching ZIP files found in the selected date range.")
        return

    # Divide the objects into batches
    object_batches = [objects_to_download[i:i + zip_batch_size] for i in range(0, num_objects, zip_batch_size)]

    with tqdm(total=len(object_batches), desc="Batches") as pbar_batch:
        run_in_processes(
            download_batch,
            [(batch, query_id, bucket_name) for batch in object_batches],
            num_processors,
            on_done=lambda: pbar_batch.update(1)
        )


def download_batch(batch, query_id, bucket_name):
    """
    Downloads a batch of files from an S3 bucket.

    Args:
        batch: List of objects to download.
        query_id: Unique identifier for the query/download session.
        bucket_name: Name of the S3 bucket.
    """
    global s3
    if s3 is None:
        s3 = get_s3_client()

    for obj in tqdm(batch, desc="Downloading files"):
        file_path = os.path.join(f'{query_id}/zipped', *obj['Key'].split('/')[1:])  # Construct local file path
        os.makedirs(os.path.dirname(file_path), exist_ok=True)  # Ensure the directory exists

        # Download file if it doesn't already exist
        if not os.path.exists(file_path):
            s3.download_file(bucket_name, obj['Key'], file_path)


def query_s3_objects_in_date_range(s3, bucket_name, path, start_date, end_date, download_file_types):
    """
    Get all S3 objects of the selected ZIP types uploaded in a specified range.
    Returns:
        List of S3 object refs in range.
    """
    objects = []
    next_token = None

    while True:
        params = {
            'Bucket': bucket_name,
            'Prefix': path
        }

        if next_token:
            params['ContinuationToken'] = next_token  # Include ContinuationToken if it exists

        response = s3.list_objects_v2(**params)
        objects.extend(response.get('Contents', []))

        if 'NextContinuationToken' in response:
            next_token = response['NextContinuationToken']
        else:
            break  # Exit the loop when there are no more objects to fetch

    filtered_objects = []
    start_date = as_utc_datetime(start_date)
    end_date = as_utc_datetime(end_date)

    for obj in objects:
        last_modified = as_utc_datetime(obj['LastModified'])
        if start_date <= last_modified <= end_date and s3_object_matches_download_type(obj, download_file_types):
            filtered_objects.append(obj)

    logging.info(f"Found {len(filtered_objects)} matching ZIP files.")

    return filtered_objects


def unzip_file(zip_file, destination_folder, image_folder):
    """
    Unzips a file to a specified destination folder. Images are face-redacted into
    image_folder; only the redacted copies are kept.

    Args:
        zip_file: Path to the zip file.
        destination_folder: Folder where files should be extracted to.
        image_folder: Folder where redacted images are written.
    """
    try:
        count_of_existing_files, count_of_non_existing_files = 0, 0

        with zipfile.ZipFile(zip_file, 'r') as zip_ref:
            for file_info in zip_ref.infolist():
                # Determine the extraction path
                extracted_file_path = os.path.join(destination_folder, file_info.filename)
                is_image = file_info.filename.lower().endswith(IMAGE_EXTENSIONS)

                if file_info.filename.endswith('.csv') or is_image:
                    # Check if the file already exists to avoid re-extraction
                    if not os.path.exists(extracted_file_path):
                        zip_ref.extract(file_info, destination_folder)
                        logging.debug(f"Extracted: {file_info.filename}")

                        if is_image:
                            processed_image_path = os.path.join(image_folder, os.path.basename(file_info.filename))
                            detect_and_redact_faces(extracted_file_path, processed_image_path)
                            count_of_non_existing_files += 1
                    else:
                        count_of_existing_files += 1

            logging.debug(f"Existing files count: {count_of_existing_files}")
            logging.debug(f"Processed new files count: {count_of_non_existing_files}")

    except Exception as e:
        logging.error(f"Error unzipping {zip_file}: {e}")


def process_child_folder_and_unzip_synchronous(parent, children, query_id):
    """
    Unzips child folders synchronously within a given parent directory.

    Args:
        parent: Parent directory containing child folders.
        children: List of child folders to be unzipped.
        query_id: Unique identifier for the operation, used in directory structuring.
    """
    for child in children:
        child_path = os.path.join(parent, child)
        destination_folder = f"{query_id}/unzipped/panelists/{os.path.basename(parent)}"
        image_folder = f"{query_id}/combined/panelists/{os.path.basename(parent)}/images"

        # Create destination and image folders
        os.makedirs(destination_folder, exist_ok=True)
        os.makedirs(image_folder, exist_ok=True)

        unzip_file(child_path, destination_folder, image_folder)


def process_child_folder_and_unzip_async(query_id):
    """
    Unzips all downloaded ZIP files for a query, one process per panelist folder.

    Args:
        query_id: Unique identifier for the query/download session.
    """
    path = os.path.join(query_id, 'zipped')
    if not os.path.isdir(path):
        logging.warning(f"No zipped folder found at {path}. Skipping unzip.")
        return

    result_dict = {}
    # Get a list of child folders with their full paths
    for root, dirs, files in os.walk(path):
        for dir_name in dirs:
            child_folder = os.path.join(root, dir_name)

            child_files = [file for file in os.listdir(child_folder) if file.endswith('.zip')]
            if child_files:
                result_dict[child_folder] = child_files

    run_in_processes(
        process_child_folder_and_unzip_synchronous,
        [(key, list(files), query_id) for key, files in result_dict.items()],
        multiprocessing.cpu_count()
    )


def process_child_folders_csvs(path):
    """
    Gathers CSV files from child folders within a given directory.

    Args:
        path (str): The path to the parent directory.

    Returns:
        dict: A mapping of child folder names to lists of their CSV files.
    """
    result_dict = {}

    for root, dirs, _ in os.walk(path):
        for dir_name in dirs:
            child_folder = os.path.join(root, dir_name)
            child_files = [os.path.join(child_folder, file) for file in os.listdir(child_folder) if
                           file.endswith('.csv')]

            # Include folder only if it contains CSV files
            if child_files:
                result_dict[dir_name] = child_files

    return result_dict


def check_face_redaction_available():
    """
    Raises before any download if images cannot be face-redacted. OpenCV 5 removed the
    Haar cascade detector used here, so opencv-python must be 4.x.
    """
    if cv2 is None:
        raise RuntimeError("opencv-python is required to redact faces in images. "
                           "Install the versions in scripts/requirements.txt.")
    if not hasattr(cv2, "CascadeClassifier"):
        raise RuntimeError(f"OpenCV {cv2.__version__} has no CascadeClassifier, so faces cannot be redacted. "
                           "Install opencv-python 4.x (see scripts/requirements.txt).")


def detect_and_redact_faces(input_image_path, output_image_path, redaction_type='redact'):
    """
    Detects faces in an image and applies redaction or blurring.

    Args:
        input_image_path (str): Path to the input image.
        output_image_path (str): Path where the processed image will be saved.
        redaction_type (str): The type of processing ('redact' or 'blur') to apply to detected faces.
    """
    global face_cascade
    check_face_redaction_available()
    if face_cascade is None:
        face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')

    image = cv2.imread(input_image_path)
    if image is None:
        # Never fall back to the unredacted file.
        logging.error(f"Could not read image {input_image_path}; it was not copied to the output.")
        return

    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    faces = face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(30, 30))

    for (x, y, w, h) in faces:
        if redaction_type == 'blur':
            blur_face(image, x, y, w, h)
        else:  # Default to 'redact' for any other input
            redact_face(image, x, y, w, h)

    cv2.imwrite(output_image_path, image)


def blur_face(image, x, y, w, h):
    """
    Applies Gaussian blurring to a face region in the image.

    Args:
        image: The image array.
        x, y: The top-left corner of the face bounding box.
        w, h: The width and height of the bounding box.
    """
    region_of_interest = image[y:y + h, x:x + w]
    blurred = cv2.GaussianBlur(region_of_interest, (99, 99), 30)
    image[y:y + h, x:x + w] = blurred


def redact_face(image, x, y, w, h):
    """
    Fills a face region in the image with black to redact it.

    Args:
        image: The image array.
        x, y: The top-left corner of the face bounding box.
        w, h: The width and height of the bounding box.
    """
    image[y:y + h, x:x + w] = (0, 0, 0)


def remove_readonly(func, path, _):
    """
    shutil.rmtree error handler that clears the read-only flag (Windows) and retries.
    """
    os.chmod(path, stat.S_IWRITE)
    func(path)


def delete_folder(folder_path):
    """
    Deletes a folder and all its contents.

    Args:
        folder_path (str): The path to the folder to be deleted.
    """
    try:
        if sys.version_info >= (3, 12):
            shutil.rmtree(folder_path, onexc=remove_readonly)
        else:
            shutil.rmtree(folder_path, onerror=remove_readonly)
        print(f"Successfully deleted the folder: {folder_path}")
    except FileNotFoundError:
        print("The folder does not exist.")
    except Exception as e:
        print(f"An error occurred: {e}")


def combine_csv_files(input_dict, query_id, remove_ocr_columns=False):
    """
        combine_csv_files(input_dict, query_id, remove_ocr_columns)

        Combines multiple CSV files grouped by specific prefixes into consolidated CSV files for each group.

        This method organizes and merges CSV files provided in an input dictionary, grouping files by their filename prefixes
        and saving the combined output in a specified directory structure.

        Parameters:
            input_dict (dict): A dictionary where the keys represent folder names and the values are lists of file paths
                               to the CSV files within those folders. For example:
                               {
                                   "folder1": ["path/to/screenshot_data_1.csv", "path/to/session_data_1.csv"],
                                   "folder2": ["path/to/app_segment_data_1.csv", "path/to/app_accessibility_data_1.csv"]
                               }
            query_id (str): A unique identifier used to create the output folder structure. The output will be saved in
                            a directory named `<query_id>/combined/panelists/<folder>/metadata`.
            remove_ocr_columns (bool): If True, omit OCR and on-screen text columns (``text``, ``ocr``, and
                                       ``ocr_text``) from the consolidated files.

        Valid Prefixes:
            The method processes files whose names start with one of CSV_PREFIXES:
            screenshot_data, app_accessibility_data, app_segment_data, session_data, package_event_data, manifest.

        Output:
            For each folder and each valid prefix, a consolidated CSV file is created in the output directory:
            `<query_id>/combined/panelists/<folder>/metadata/<prefix>-consolidated.csv`.

        Details:
        - Columns are matched by name, not position. The output has every column seen in the group, in first-seen
          order, and rows from files without a column leave it empty. This keeps data from different app versions
          aligned in one file.
        - If a folder has no files with valid prefixes, it is skipped.

        Exceptions:
            - May raise `FileNotFoundError` if a specified file does not exist.
            - May raise `pandas.errors.EmptyDataError` if any of the input files are empty or corrupted.
    """
    output_folder = f"{query_id}/combined"

    for folder, csv_files in input_dict.items():
        prefix_dict = {}

        # Group CSV files by prefix
        for csv_file in csv_files:
            filename = os.path.basename(csv_file)

            for prefix in CSV_PREFIXES:
                if filename.startswith(prefix):
                    prefix_dict.setdefault(prefix, []).append(csv_file)

        panelist_folder = os.path.join(os.path.join(output_folder, 'panelists'), folder + '/metadata')
        print(panelist_folder)
        pathlib.Path(panelist_folder).mkdir(parents=True, exist_ok=True)

        # Combine CSV files within each prefix group
        for prefix, files in prefix_dict.items():
            combined_csv = os.path.join(panelist_folder, f"{prefix}-consolidated.csv")

            columns = []
            for file in files:
                for column in read_csv_header(file):
                    if column not in columns:
                        columns.append(column)
            if remove_ocr_columns:
                columns = [column for column in columns if column.lower() not in OCR_COLUMN_NAMES]

            header_written = False
            for file in files:
                df = read_downloaded_csv(file).reindex(columns=columns)
                if not header_written:
                    df.to_csv(combined_csv, index=False)
                    header_written = True
                else:
                    df.to_csv(combined_csv, mode='a', header=False, index=False)


def calculate_screenshot_daily_stats(query_id):
    """
    Calculates screenshot volume summaries for a consolidated query.

    Outputs are written to <query_id>/combined/summary/:
        images_per_participant_day.csv
        images_per_day.csv
        participant_statistics.csv
        overall_statistics.csv

    When the screenshot data has a ``state`` column (UMN builds today), these are also written:
        state_type_per_participant_day.csv
        state_type_per_day.csv
        normal_data_per_participant_day.csv
        normal_data_per_participant.csv
        participants_without_normal_3_consecutive_days.csv

    Returns:
        dict: Overall summary metrics.
    """
    panelists_root = os.path.join(query_id, "combined", "panelists")
    summary_root = os.path.join(query_id, "combined", "summary")

    if not os.path.isdir(panelists_root):
        logging.warning(f"No panelists folder found at {panelists_root}. Skipping screenshot stats.")
        return {}

    screenshot_rows = []

    for panelist_folder in sorted(os.listdir(panelists_root)):
        screenshot_csv = os.path.join(
            panelists_root,
            panelist_folder,
            "metadata",
            "screenshot_data-consolidated.csv"
        )

        if not os.path.isfile(screenshot_csv):
            logging.info(f"No screenshot data found for panelist folder {panelist_folder}.")
            continue

        try:
            df = pd.read_csv(
                screenshot_csv,
                usecols=lambda col: col in {
                    "id_user",
                    "file",
                    "state",
                    "t_epoch_ts_ms",
                    "t_natural_day_ts"
                }
            )
        except pd.errors.EmptyDataError:
            logging.warning(f"Screenshot data is empty for panelist folder {panelist_folder}.")
            continue

        if df.empty:
            continue

        if "id_user" not in df.columns:
            df["id_user"] = panelist_folder

        if "t_natural_day_ts" in df.columns and df["t_natural_day_ts"].notna().any():
            df["screenshot_day"] = pd.to_datetime(df["t_natural_day_ts"], errors="coerce").dt.date
        elif "t_epoch_ts_ms" in df.columns:
            df["screenshot_day"] = pd.to_datetime(
                df["t_epoch_ts_ms"],
                unit="ms",
                errors="coerce"
            ).dt.date
        else:
            logging.warning(
                f"No usable screenshot date column found for panelist folder {panelist_folder}."
            )
            continue

        df = df.dropna(subset=["screenshot_day"])
        if df.empty:
            continue

        if "file" not in df.columns:
            df["file"] = ""

        df["file"] = df["file"].fillna("").astype(str)
        df["image_file"] = df["file"].apply(lambda value: os.path.basename(value) if value else "")
        screenshot_rows.append(df)

    if not screenshot_rows:
        logging.warning("No screenshot rows found. Skipping screenshot stats.")
        return {}

    pathlib.Path(summary_root).mkdir(parents=True, exist_ok=True)

    screenshot_rows_df = pd.concat(screenshot_rows, ignore_index=True)
    # Force id_user to string after concat so NaN floats and "123.0" read the same everywhere.
    screenshot_rows_df["id_user"] = (
        screenshot_rows_df["id_user"]
        .fillna("unknown")
        .astype(str)
        .str.replace(r"\.0$", "", regex=True)
    )
    duplicate_subset = [
        column for column in ["id_user", "screenshot_day", "file", "t_epoch_ts_ms"]
        if column in screenshot_rows_df.columns
    ]
    screenshot_rows_df = screenshot_rows_df.drop_duplicates(subset=duplicate_subset)

    participant_day_counts = (
        screenshot_rows_df.groupby(["id_user", "screenshot_day"])
        .agg(
            screenshot_count=("id_user", "size"),
            image_count=("image_file", lambda values: values[values != ""].nunique())
        )
        .reset_index()
        .sort_values(["id_user", "screenshot_day"])
    )
    participant_day_counts.loc[
        participant_day_counts["image_count"] == 0,
        "image_count"
    ] = participant_day_counts["screenshot_count"]

    images_per_day = (
        participant_day_counts.groupby("screenshot_day")
        .agg(
            participants=("id_user", "nunique"),
            screenshot_count=("screenshot_count", "sum"),
            image_count=("image_count", "sum")
        )
        .reset_index()
        .sort_values("screenshot_day")
    )

    participant_summary = (
        participant_day_counts.groupby("id_user")
        .agg(
            active_days=("screenshot_count", "count"),
            first_screenshot_day=("screenshot_day", "min"),
            last_screenshot_day=("screenshot_day", "max"),
            total_screenshots=("screenshot_count", "sum"),
            total_images=("image_count", "sum"),
            mean_images_per_active_day=("image_count", "mean"),
            median_images_per_active_day=("image_count", "median"),
            min_images_per_active_day=("image_count", "min"),
            max_images_per_active_day=("image_count", "max"),
            mean_screenshots_per_active_day=("screenshot_count", "mean"),
            median_screenshots_per_active_day=("screenshot_count", "median"),
            min_screenshots_per_active_day=("screenshot_count", "min"),
            max_screenshots_per_active_day=("screenshot_count", "max")
        )
        .reset_index()
        .sort_values("id_user")
    )

    overall_summary = {
        "participants": int(participant_summary["id_user"].nunique()),
        "calendar_days": int(images_per_day["screenshot_day"].nunique()),
        "participant_days": int(len(participant_day_counts)),
        "total_screenshots": int(participant_day_counts["screenshot_count"].sum()),
        "total_images": int(participant_day_counts["image_count"].sum()),
        "mean_images_per_participant_day": float(participant_day_counts["image_count"].mean()),
        "median_images_per_participant_day": float(participant_day_counts["image_count"].median()),
        "mean_screenshots_per_participant_day": float(participant_day_counts["screenshot_count"].mean()),
        "median_screenshots_per_participant_day": float(participant_day_counts["screenshot_count"].median()),
        "mean_images_per_calendar_day": float(images_per_day["image_count"].mean()),
        "median_images_per_calendar_day": float(images_per_day["image_count"].median()),
        "mean_of_participant_daily_means": float(
            participant_summary["mean_images_per_active_day"].mean()
        ),
        "median_of_participant_daily_means": float(
            participant_summary["mean_images_per_active_day"].median()
        ),
        "mean_of_participant_daily_medians": float(
            participant_summary["median_images_per_active_day"].mean()
        ),
        "median_of_participant_daily_medians": float(
            participant_summary["median_images_per_active_day"].median()
        )
    }

    if "state" in screenshot_rows_df.columns:
        participant_summary = write_state_summaries(
            screenshot_rows_df, participant_summary, overall_summary, summary_root
        )
    else:
        logging.info("Screenshot data has no state column. Skipping state summaries.")

    participant_day_counts.to_csv(
        os.path.join(summary_root, "images_per_participant_day.csv"),
        index=False
    )
    images_per_day.to_csv(
        os.path.join(summary_root, "images_per_day.csv"),
        index=False
    )
    participant_summary.to_csv(
        os.path.join(summary_root, "participant_statistics.csv"),
        index=False
    )
    pd.DataFrame([overall_summary]).to_csv(
        os.path.join(summary_root, "overall_statistics.csv"),
        index=False
    )

    print("Screenshot stats complete:")
    print(f"  Participants: {overall_summary['participants']}")
    print(f"  Calendar days: {overall_summary['calendar_days']}")
    print(f"  Participant-days: {overall_summary['participant_days']}")
    if "total_normal_screenshots" in overall_summary:
        print(f"  Total Normal screenshots: {overall_summary['total_normal_screenshots']}")
        print(
            "  Participants with 3+ consecutive days without Normal data: "
            f"{overall_summary['participants_without_normal_3_consecutive_days']}"
        )
    print(
        "  Mean images per participant-day: "
        f"{overall_summary['mean_images_per_participant_day']:.2f}"
    )
    print(
        "  Median images per participant-day: "
        f"{overall_summary['median_images_per_participant_day']:.2f}"
    )

    return overall_summary


def write_state_summaries(screenshot_rows_df, participant_summary, overall_summary, summary_root):
    """
    Writes per-state screenshot summaries and flags participants with 3 or more consecutive
    days without any "Normal" screenshot. Adds the state metrics to participant_summary
    (returned) and overall_summary (in place).
    """
    df = screenshot_rows_df.copy()
    df["state"] = df["state"].fillna("unknown").astype(str).str.strip()
    df.loc[df["state"] == "", "state"] = "unknown"
    df["is_normal_state"] = df["state"].str.casefold() == "normal"

    state_type_per_participant_day = (
        df.groupby(["id_user", "screenshot_day", "state"])
        .size()
        .reset_index(name="screenshot_count")
        .sort_values(["id_user", "screenshot_day", "state"])
    )

    state_type_per_day = (
        df.groupby(["screenshot_day", "state"])
        .agg(
            participants=("id_user", "nunique"),
            screenshot_count=("id_user", "size")
        )
        .reset_index()
        .sort_values(["screenshot_day", "state"])
    )

    normal_data_per_participant_day = (
        df.groupby(["id_user", "screenshot_day"])
        .agg(
            normal_screenshot_count=("is_normal_state", "sum"),
            total_screenshot_count=("id_user", "size")
        )
        .reset_index()
        .sort_values(["id_user", "screenshot_day"])
    )
    normal_data_per_participant_day["normal_screenshot_count"] = (
        normal_data_per_participant_day["normal_screenshot_count"].astype(int)
    )

    normal_data_per_participant = (
        normal_data_per_participant_day.groupby("id_user")
        .agg(
            days_observed=("screenshot_day", "count"),
            days_with_normal_data=("normal_screenshot_count", lambda values: int((values > 0).sum())),
            total_normal_screenshots=("normal_screenshot_count", "sum"),
            total_screenshots=("total_screenshot_count", "sum"),
            first_observed_day=("screenshot_day", "min"),
            last_observed_day=("screenshot_day", "max")
        )
        .reset_index()
        .sort_values("id_user")
    )
    normal_data_per_participant["total_normal_screenshots"] = (
        normal_data_per_participant["total_normal_screenshots"].astype(int)
    )
    normal_data_per_participant["total_screenshots"] = (
        normal_data_per_participant["total_screenshots"].astype(int)
    )
    normal_data_per_participant["normal_share"] = (
        normal_data_per_participant["total_normal_screenshots"]
        / normal_data_per_participant["total_screenshots"]
    ).fillna(0)

    all_days = pd.date_range(df["screenshot_day"].min(), df["screenshot_day"].max(), freq="D").date
    normal_count_lookup = {
        (row.id_user, row.screenshot_day): int(row.normal_screenshot_count)
        for row in normal_data_per_participant_day.itertuples(index=False)
    }
    missing_normal_runs = []

    for participant_id in sorted(df["id_user"].unique()):
        run_start = None
        run_length = 0

        for day in all_days:
            if normal_count_lookup.get((participant_id, day), 0) == 0:
                if run_start is None:
                    run_start = day
                run_length += 1
                continue

            if run_length >= 3:
                missing_normal_runs.append({
                    "id_user": participant_id,
                    "start_date": run_start,
                    "end_date": day - timedelta(days=1),
                    "consecutive_days_without_normal": run_length
                })
            run_start = None
            run_length = 0

        if run_length >= 3:
            missing_normal_runs.append({
                "id_user": participant_id,
                "start_date": run_start,
                "end_date": all_days[-1],
                "consecutive_days_without_normal": run_length
            })

    participants_without_normal_3_consecutive_days = pd.DataFrame(
        missing_normal_runs,
        columns=["id_user", "start_date", "end_date", "consecutive_days_without_normal"]
    )

    participant_summary = participant_summary.merge(
        normal_data_per_participant[
            ["id_user", "days_with_normal_data", "total_normal_screenshots", "normal_share"]
        ],
        on="id_user",
        how="left"
    )
    longest_missing_normal_run = (
        participants_without_normal_3_consecutive_days.groupby("id_user")[
            "consecutive_days_without_normal"
        ]
        .max()
        .reset_index(name="longest_consecutive_days_without_normal")
        if not participants_without_normal_3_consecutive_days.empty
        else pd.DataFrame(columns=["id_user", "longest_consecutive_days_without_normal"])
    )
    participant_summary = participant_summary.merge(longest_missing_normal_run, on="id_user", how="left")
    for column in ["days_with_normal_data", "total_normal_screenshots", "longest_consecutive_days_without_normal"]:
        participant_summary[column] = participant_summary[column].fillna(0).astype(int)
    participant_summary["normal_share"] = participant_summary["normal_share"].fillna(0)

    overall_summary["total_normal_screenshots"] = int(
        normal_data_per_participant["total_normal_screenshots"].sum()
    )
    overall_summary["participants_with_normal_data"] = int(
        (normal_data_per_participant["total_normal_screenshots"] > 0).sum()
    )
    overall_summary["participants_without_normal_3_consecutive_days"] = int(
        participants_without_normal_3_consecutive_days["id_user"].nunique()
    )
    overall_summary["state_types"] = int(df["state"].nunique())

    state_type_per_participant_day.to_csv(
        os.path.join(summary_root, "state_type_per_participant_day.csv"),
        index=False
    )
    state_type_per_day.to_csv(
        os.path.join(summary_root, "state_type_per_day.csv"),
        index=False
    )
    normal_data_per_participant_day.to_csv(
        os.path.join(summary_root, "normal_data_per_participant_day.csv"),
        index=False
    )
    normal_data_per_participant.to_csv(
        os.path.join(summary_root, "normal_data_per_participant.csv"),
        index=False
    )
    participants_without_normal_3_consecutive_days.to_csv(
        os.path.join(summary_root, "participants_without_normal_3_consecutive_days.csv"),
        index=False
    )

    return participant_summary


# Step 7: Main function to orchestrate the workflow
def main(argv=None):
    global s3
    args = parse_args(argv)
    query_id = args.query_id or f"query_{datetime.now().strftime('%Y%m%d_%H%M%S')}"

    s3 = connect_to_s3()
    bucket_name = get_bucket_name(args.bucket)
    bucket_name, path = get_bucket_and_path(s3, bucket_name)
    if args.start is not None or args.end is not None:
        start_date, end_date = parse_date_range(args.start, args.end)
    else:
        start_date, end_date = get_date_range_from_user()
    download_file_types = ZIP_TYPE_CHOICES[args.zip_types] if args.zip_types else get_download_file_types_from_user()
    if "image" in download_file_types:
        check_face_redaction_available()
    if "csv" not in download_file_types:
        remove_ocr_columns = False
    elif args.remove_ocr is not None:
        remove_ocr_columns = args.remove_ocr
    else:
        remove_ocr_columns = should_remove_ocr_from_consolidated_csvs()

    zip_batch_size, num_processors = set_batch_and_processors(
        query_id,
        download_file_types,
        remove_ocr_columns,
        args.batch_size,
        args.processors,
    )
    download_zip_files_in_batches(
        s3,
        bucket_name,
        path,
        start_date,
        end_date,
        zip_batch_size,
        num_processors,
        query_id,
        download_file_types
    )
    process_child_folder_and_unzip_async(query_id)

    result = process_child_folders_csvs(os.path.join(query_id, 'unzipped'))
    if result:
        combine_csv_files(result, query_id, remove_ocr_columns)
        calculate_screenshot_daily_stats(query_id)
    else:
        logging.info("No CSV files found. Skipping CSV consolidation.")

    delete_folder(os.path.join(query_id, "unzipped"))
    delete_folder(os.path.join(query_id, "zipped"))
    print(f"Output written to {os.path.join(query_id, 'combined')}")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    main()
