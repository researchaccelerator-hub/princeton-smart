import multiprocessing
import json
import pathlib
import shutil
import zipfile
import pandas as pd
import pytz
from tqdm import tqdm
import logging
import boto3
from datetime import datetime, timedelta
import os
import cv2

s3 = boto3.client('s3')

# Load the pre-trained face detection model
face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + 'haarcascade_frontalface_default.xml')


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
        else:
            aws_access_key = input("Enter AWS Access Key: ")
            aws_secret_key = input("Enter AWS Secret Key: ")
            return boto3.client('s3', aws_access_key_id=aws_access_key, aws_secret_access_key=aws_secret_key)
    else:
        aws_access_key = input("Enter AWS Access Key: ")
        aws_secret_key = input("Enter AWS Secret Key: ")
        return boto3.client('s3', aws_access_key_id=aws_access_key, aws_secret_access_key=aws_secret_key)

    return boto3.client('s3')


def get_date_range_from_user():
    """
    Prompts the user to input a date range and returns the start and end dates as datetime objects.
    Returns:
        tuple: start_date, end_date
    """
    start_date_str = input("Enter the start date (YYYY-MM-DD, or press Enter for 1 year ago): ")
    end_date_str = input("Enter the end date (YYYY-MM-DD, or press Enter for today): ")

    # Set default or specified start date
    if not start_date_str:
        start_date = datetime.now() - timedelta(days=365)
    else:
        start_date = datetime.strptime(start_date_str, "%Y-%m-%d")

    # Set default or specified end date
    if not end_date_str:
        end_date = datetime.now()
    else:
        end_date = datetime.strptime(end_date_str, "%Y-%m-%d")

    if start_date > end_date:
        raise ValueError("Start date cannot be after end date.")
    return start_date, end_date


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
    response = s3.list_objects_v2(Bucket=bucket_name, Prefix=path, Delimiter='/')
    common_prefixes = response.get('CommonPrefixes', [])
    return [prefix.get('Prefix') for prefix in common_prefixes]


def get_bucket_and_path():
    """
    Interactively select a directory path within an AWS S3 bucket based on user input.

    Returns:
        Tuple containing the selected bucket name and directory path.
    """
    default_bucket_name = "screenlake-zip-prod"
    s3 = boto3.client('s3')

    # Prompt for the S3 bucket (default: screenlake-zip-prod)
    bucket_name = input(f"Enter the S3 bucket name (default: {default_bucket_name}): ") or default_bucket_name

    # Start with the base path and list folders
    base_path = "academia/tenant/"
    selected_path = base_path
    selected_count = 0
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
            choice = '1'
            selected_count = selected_count + 1
            header = 'Here is a list of panelist who will have CSVs created'

            return bucket_name, selected_path
        else:
            # Display folder options for user selection
            logging.info(f'Select a {header}:')
            for i, folder in enumerate(folders, start=1):
                split_folders = folder.split('/')
                folder_name = split_folders[len(split_folders) - 2]
                print(f"{i}. {folder_name}")

            choice = input("Enter the number corresponding to your choice (q to quit): ")
            selected_count = selected_count + 1

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


# Step 3: Query by date range
def query_by_date_range(s3, bucket_name, path, start_date, end_date):
    """
    Filters objects in an S3 bucket path by a date range.
    Args:
        s3: Boto3 S3 client
        bucket_name: Name of the S3 bucket
        path: Path within the S3 bucket
        start_date: Start of the date range as datetime
        end_date: End of the date range as datetime
    Returns:
        list: Filtered S3 objects within the date range
    """
    objects = []
    next_token = None

    while True:
        params = {
            'Bucket': bucket_name,
            'Prefix': path,
            'ContinuationToken': next_token
        }

        response = s3.list_objects_v2(**params)
        objects.extend(response.get('Contents', []))
        next_token = response.get('NextContinuationToken')

        if not next_token:
            break

    filtered_objects = []

    for obj in objects:
        last_modified = obj['LastModified']
        if start_date <= last_modified <= end_date:
            filtered_objects.append(obj)

    return filtered_objects


# Step 4: Set batch and processor parameters
def set_batch_and_processors():
    """
    Sets up the batch size for zip file downloads and the number of processors to use.
    Also prepares the directory structure for storing query results and configures a query ID.

    Returns:
        A tuple containing the batch size, number of processors, and the query ID.
    """
    # Prompt the user for batch size and number of processors with default values
    try:
        zip_batch_size = int(input("Enter the batch size for zip file downloads (default: 25): ") or 25)
        num_processors = int(input("Enter the number of processors (default: 4): ") or 4)
    except ValueError as e:
        logging.error("Invalid input, please enter a number.")
        raise e

    # Generate a unique query ID for each session to avoid conflicts
    query_id = f"query_1234"

    # Configuration dictionary for the query
    query_config = {
        "queryId": query_id,
        "batchSize": zip_batch_size,
        "numFilesToDownload": 0,  # Initial setup, no files to download yet
        "numFilesDownloaded": 0,  # Tracker for downloaded files
        "lastBatchBeginId": ""  # Placeholder for tracking batches
    }

    # Prepare directories for zipped and unzipped files
    directories = {
        "base": query_id,
        "zipped": os.path.join(query_id, "zipped"),
        "unzipped": os.path.join(query_id, "unzipped")
    }

    # Create the directories if they don't exist
    for directory in directories.values():
        os.makedirs(directory, exist_ok=True)

    # Save the query configuration to a JSON file
    config_path = os.path.join(query_id, "query_config.json")
    with open(config_path, "w") as config_file:
        json.dump(query_config, config_file)

    logging.info(f"Configuration saved to {config_path}.")
    return zip_batch_size, num_processors, query_id


# Step 5: Download zip files in batches
def download_zip_files_in_batches(s3, bucket_name, path, start_date, end_date, zip_batch_size, num_processors,
                                  query_id):
    """
     Downloads objects from S3 in batches, using multiple processes.

     Args:
         s3_client: Boto3 S3 client instance.
         bucket_name: Name of the S3 bucket.
         path: Path within the S3 bucket from which to download objects.
         start_date: Start date for querying objects.
         end_date: End date for querying objects.
         zip_batch_size: Number of files to download in each batch.
         num_processors: Number of parallel processes to use for downloading.
         query_id: Unique identifier for the query/download session.
     """
    objects_to_download = query_s3_objects_in_date_range(s3, bucket_name, path, start_date, end_date)
    num_objects = len(objects_to_download)

    # Divide the objects into batches
    object_batches = [objects_to_download[i:i + zip_batch_size] for i in range(0, num_objects, zip_batch_size)]

    processes = []
    with tqdm(total=len(object_batches), desc="Batches") as pbar_batch:
        for batch in object_batches:
            if len(processes) == num_processors or len(processes) == len(object_batches):
                for process in processes:
                    pbar_batch.update(1)
                    process.join()

                processes = []
            else:
                process = multiprocessing.Process(target=download_batch,
                                                  args=(batch, query_id, bucket_name))
                process.start()
                processes.append(process)

            if len(processes) == len(object_batches):
                for process in processes:
                    process.join()


def download_batch(batch, query_id, bucket_name):
    """
    Downloads a batch of files from an S3 bucket.

    Args:
        s3_client: Boto3 S3 client instance.
        batch: List of objects to download.
        query_id: Unique identifier for the query/download session.
        bucket_name: Name of the S3 bucket.
    """
    for obj in tqdm(batch, desc="Downloading files"):
        file_path = os.path.join(f'{query_id}/zipped', *obj['Key'].split('/')[1:])  # Construct local file path
        os.makedirs(os.path.dirname(file_path), exist_ok=True)  # Ensure the directory exists

        # Download file if it doesn't already exist
        if not os.path.exists(file_path):
            s3.download_file(bucket_name, obj['Key'], file_path)


def query_s3_objects_in_date_range(s3, bucket_name, path, start_date, end_date):
    """
    Get all S3 objects in a specified range.
    Returns:
        List of S3 object refs in range.
    """
    objects = []
    next_token = None

    count = 0  # Initialize a count variable to track the number of files fetched
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

    for obj in objects:
        start_date = start_date.replace(tzinfo=pytz.UTC)
        end_date = end_date.replace(tzinfo=pytz.UTC)
        last_modified = obj['LastModified']
        last_modified = last_modified.replace(tzinfo=pytz.UTC)  # Make sure LastModified is timezone-aware
        if start_date <= last_modified <= end_date:
            filtered_objects.append(obj)
            count += 1  # Increment the count for each file fetched
            logging.info(f'Files fetched: {count}", end="\r')  # Print the count with carriage return to overwrite the line

    # Print a newline to separate the progress count
    logging.info("")

    return filtered_objects


def unzip_file(zip_file, destination_folder, image_folder):
    """
    Unzips a file to a specified destination folder, with additional processing for images.

    Args:
        zip_file: Path to the zip file.
        destination_folder: Folder where files should be extracted to.
        image_folder: Folder where images should be stored after processing.
    """
    try:
        count_of_existing_files, count_of_non_existing_files = 0, 0

        with zipfile.ZipFile(zip_file, 'r') as zip_ref:
            for file_info in zip_ref.infolist():
                # Determine the extraction path
                extracted_file_path = os.path.join(destination_folder, file_info.filename)
                is_image = file_info.filename.lower().endswith(('.jpg', '.jpeg'))

                if file_info.filename.endswith('.csv') or is_image:
                    # Check if the file already exists to avoid re-extraction
                    if not os.path.exists(extracted_file_path):
                        zip_ref.extract(file_info, destination_folder)
                        logging.info(f"Extracted: {file_info.filename}")

                        if is_image:
                            # Additional processing for images
                            processed_image_path = os.path.join(image_folder, file_info.filename)
                            detect_and_redact_faces(extracted_file_path, processed_image_path)
                            count_of_non_existing_files += 1
                    else:
                        count_of_existing_files += 1

            logging.info(f"Existing files count: {count_of_existing_files}")
            logging.info(f"Processed new files count: {count_of_non_existing_files}")

        # Attempt to remove the original zip file after extraction
        try:
            # os.remove(zip_file)
            logging.info(f"Removed zip file: {zip_file}")
        except OSError as e:
            logging.error(f"Error deleting zip file {zip_file}: {e}")

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


def process_child_folder_and_unzip_async(path):
    """
    Asynchronously unzips all child folders within a specified path.

    Args:
        path: Path containing child folders to be unzipped.
    """
    zipped_child_folders = []
    result_dict = {}
    # Get a list of child folders with their full paths
    for root, dirs, files in os.walk(path):
        for dir_name in dirs:
            child_folder = os.path.join(root, dir_name)
            zipped_child_folders.append(child_folder)

            child_files = [file for file in os.listdir(child_folder) if file.endswith('.zip')]
            result_dict[child_folder] = child_files

    # Create a pool of worker processes
    num_processes = multiprocessing.cpu_count()
    processes = []

    for key in result_dict:
        if len(processes) == num_processes:
            for process in processes:
                process.join()

            processes = []
        else:
            process = multiprocessing.Process(target=process_child_folder_and_unzip_synchronous,
                                              args=(key, list(result_dict[key]), path))
            process.start()
            processes.append(process)

        if len(processes) == len(zipped_child_folders):
            for process in processes:
                process.join()


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


def detect_and_redact_faces(input_image_path, output_image_path, redaction_type='redact'):
    """
    Detects faces in an image and applies redaction or blurring.

    Args:
        input_image_path (str): Path to the input image.
        output_image_path (str): Path where the processed image will be saved.
        redaction_type (str): The type of processing ('redact' or 'blur') to apply to detected faces.
    """
    image = cv2.imread(input_image_path)
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


def delete_folder(folder_path):
    """
    Deletes a folder and all its contents.

    Args:
        folder_path (str): The path to the folder to be deleted.
    """
    try:
        # Remove the folder and all its contents
        shutil.rmtree(folder_path)
        print(f"Successfully deleted the folder: {folder_path}")
    except FileNotFoundError:
        print("The folder does not exist.")
    except Exception as e:
        print(f"An error occurred: {e}")


def combine_csv_files(input_dict, query_id):
    """
        combine_csv_files(input_dict, query_id)

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

        Valid Prefixes:
            The method processes files whose names start with one of the following prefixes:
            - "screenshot_data"
            - "app_accessibility_data"
            - "app_segment_data"
            - "session_data"

        Output:
            For each folder and each valid prefix, a consolidated CSV file is created in the output directory:
            `<query_id>/combined/panelists/<folder>/metadata/<prefix>-consolidated.csv`.

        Process:
        1. Groups the CSV files within each folder by their prefixes based on valid prefixes.
        2. Creates the necessary directory structure for storing the consolidated files.
        3. Merges all CSV files in each group into a single CSV file with a consolidated filename.

        Details:
        - The method writes the header from the first CSV file in each group and appends data from subsequent files without headers.
        - If a folder has no files with valid prefixes, it is skipped.

        Exceptions:
            - May raise `FileNotFoundError` if a specified file does not exist.
            - May raise `pandas.errors.EmptyDataError` if any of the input files are empty or corrupted.

        Example Usage:
            input_dict = {
                "panelist1": ["data/screenshot_data_1.csv", "data/session_data_1.csv"],
                "panelist2": ["data/app_segment_data_2.csv", "data/app_accessibility_data_2.csv"]
            }
            query_id = "query_123"

            combine_csv_files(input_dict, query_id)

            # This will produce consolidated CSV files in the following structure:
            # query_123/combined/panelists/panelist1/metadata/screenshot_data-consolidated.csv
            # query_123/combined/panelists/panelist1/metadata/session_data-consolidated.csv
            # query_123/combined/panelists/panelist2/metadata/app_segment_data-consolidated.csv
            # query_123/combined/panelists/panelist2/metadata/app_accessibility_data-consolidated.csv
    """
    output_folder = f"{query_id}/combined"

    for folder, csv_files in input_dict.items():
        prefix_dict = {}

        # Group CSV files by prefixƒ
        for csv_file in csv_files:
            filename = os.path.basename(csv_file)

            # Check if the prefix matches one of the specified prefixes
            valid_prefixes = ["screenshot_data", "app_accessibility_data", "app_segment_data", "session_data"]
            for prefix in valid_prefixes:
                if filename.startswith(prefix):
                    if prefix not in prefix_dict:
                        prefix_dict[prefix] = []
                        prefix_dict[prefix].append(csv_file)
                    else:
                        prefix_dict[prefix].append(csv_file)

        panelist_folder = os.path.join(os.path.join(output_folder, 'panelists'), folder + '/metadata')
        print(panelist_folder)
        pathlib.Path(panelist_folder).mkdir(parents=True, exist_ok=True)

        # Combine CSV files within each prefix group
        for prefix, files in prefix_dict.items():
            combined_csv = os.path.join(panelist_folder, f"{prefix}-consolidated.csv")
            header_written = False

            for file in files:
                df = pd.read_csv(file)
                if not header_written:
                    df.to_csv(combined_csv, index=False)
                    header_written = True
                else:
                    df.to_csv(combined_csv, mode='a', header=False, index=False)


# Step 7: Main function to orchestrate the workflow
def main():
    s3 = connect_to_s3()
    bucket_name, path = get_bucket_and_path()
    start_date, end_date = get_date_range_from_user()

    zip_batch_size, num_processors, query_id = set_batch_and_processors()
    download_zip_files_in_batches(s3, bucket_name, path, start_date, end_date, zip_batch_size, num_processors, query_id)
    process_child_folder_and_unzip_async(query_id)

    result = process_child_folders_csvs(os.path.join(query_id, 'unzipped'))
    combine_csv_files(result, query_id)

    delete_folder(os.path.join(query_id, "unzipped"))
    delete_folder(os.path.join(query_id, "zipped"))


if __name__ == "__main__":
    main()
