import os
import sys
import time
import zipfile
from datetime import datetime, timezone

import numpy as np
import pandas as pd
import pytest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import consolidatecsvs as cc  # noqa: E402


class FakeS3:
    """Returns canned list_objects_v2 pages in order and records the requests."""

    def __init__(self, pages):
        self.pages = list(pages)
        self.requests = []

    def list_objects_v2(self, **params):
        self.requests.append(params)
        return self.pages.pop(0)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", newline="") as f:
        f.write(text)
    return str(path)


@pytest.fixture
def chicago_tz():
    old = os.environ.get("TZ")
    os.environ["TZ"] = "America/Chicago"
    time.tzset()
    yield
    if old is None:
        del os.environ["TZ"]
    else:
        os.environ["TZ"] = old
    time.tzset()


def test_read_downloaded_csv_rejoins_unquoted_commas_in_last_column(tmp_path):
    path = write(tmp_path / "a.csv", 'id_user,apk,text\n1,com.a,"quoted, fine"\n2,com.b,hello, world, again\n3,com.c\n')

    df = cc.read_downloaded_csv(path)

    assert list(df["text"]) == ["quoted, fine", "hello, world, again", ""]
    assert list(df["apk"]) == ["com.a", "com.b", "com.c"]


@pytest.mark.parametrize("key,types,expected", [
    ("p/csv_zip_abc.zip", {"csv"}, True),             # SMART name
    ("p/csv_zip_abc_12.zip", {"csv"}, True),          # UMN name
    ("p/image_zip_abc_12.zip", {"csv"}, False),
    ("p/image_zip_abc_12.zip", {"image"}, True),
    ("p/csv_zip_abc.zip", {"csv", "image"}, True),
    ("p/manifest_abc.csv", {"csv", "image"}, False),
])
def test_zip_type_matching_accepts_both_naming_schemes(key, types, expected):
    assert cc.s3_object_matches_download_type({"Key": key}, types) is expected


def test_parse_date_range_is_inclusive_whole_days():
    start, end = cc.parse_date_range("2026-07-01", "2026-07-27")

    assert start == datetime(2026, 7, 1, 0, 0, 0)
    assert end == datetime(2026, 7, 27, 23, 59, 59, 999999)


def test_parse_date_range_rejects_reversed_range():
    with pytest.raises(ValueError):
        cc.parse_date_range("2026-07-27", "2026-07-01")


def test_naive_dates_are_local_time(chicago_tz):
    # 23:59 CDT is 04:59 UTC the next day; treating it as UTC would drop five hours.
    assert cc.as_utc_datetime(datetime(2026, 7, 27, 23, 59)) == datetime(2026, 7, 28, 4, 59, tzinfo=timezone.utc)


def test_list_folders_follows_continuation_tokens():
    s3 = FakeS3([
        {"CommonPrefixes": [{"Prefix": "a/"}], "IsTruncated": True, "NextContinuationToken": "t1"},
        {"CommonPrefixes": [{"Prefix": "b/"}], "IsTruncated": False},
    ])

    assert cc.list_folders_in_path(s3, "bucket", "root/") == ["a/", "b/"]
    assert s3.requests[1]["ContinuationToken"] == "t1"


def test_query_filters_by_type_and_local_date_range_across_pages(chicago_tz):
    in_range = datetime(2026, 7, 28, 3, 0, tzinfo=timezone.utc)  # 22:00 on the 27th in Chicago
    out_of_range = datetime(2026, 7, 28, 6, 0, tzinfo=timezone.utc)
    s3 = FakeS3([
        {"Contents": [
            {"Key": "p/x/csv_zip_1.zip", "LastModified": in_range},
            {"Key": "p/x/image_zip_1_10.zip", "LastModified": in_range},
        ], "NextContinuationToken": "t"},
        {"Contents": [
            {"Key": "p/x/csv_zip_2_10.zip", "LastModified": in_range},
            {"Key": "p/x/csv_zip_3.zip", "LastModified": out_of_range},
        ]},
    ])
    start, end = cc.parse_date_range("2026-07-27", "2026-07-27")

    found = cc.query_s3_objects_in_date_range(s3, "bucket", "p/", start, end, {"csv"})

    assert [o["Key"] for o in found] == ["p/x/csv_zip_1.zip", "p/x/csv_zip_2_10.zip"]


def test_run_in_processes_starts_every_job_once(tmp_path):
    # The previous loop skipped the job that arrived when the pool was full, so batches
    # 5 and 10 of 10 were never downloaded with 4 processes.
    targets = [(str(tmp_path / f"job{i}"),) for i in range(10)]
    done = []

    cc.run_in_processes(os.makedirs, targets, max_processes=4, on_done=lambda: done.append(1))

    assert sorted(os.listdir(tmp_path)) == sorted(f"job{i}" for i in range(10))
    assert len(done) == 10


def test_combine_aligns_columns_adds_package_events_and_removes_ocr(tmp_path):
    folder = tmp_path / "unzipped" / "panelists" / "hash1"
    files = [
        write(folder / "screenshot_data_csv_1.csv", "id_user,file,text,t_epoch_ts_ms\n1,a.jpg,secret,100\n"),
        # A newer schema with an extra column, in a different position.
        write(folder / "screenshot_data_csv_2.csv", "id_user,state,file,text,t_epoch_ts_ms\n1,Normal,b.jpg,secret,200\n"),
        write(folder / "app_accessibility_data_csv_1.csv", "id_user,type,text\n1,click,secret\n"),
        write(folder / "package_event_data_csv_1.csv", "id_user,apk,event_type\n1,com.a,INSTALLED\n"),
        write(folder / "manifest_1.csv", "app_version,schema_version\n1.53.0,3\n"),
    ]
    query_id = str(tmp_path / "q")

    cc.combine_csv_files({"hash1": files}, query_id, remove_ocr_columns=True)

    metadata = os.path.join(query_id, "combined", "panelists", "hash1", "metadata")
    screenshots = pd.read_csv(os.path.join(metadata, "screenshot_data-consolidated.csv"))
    assert list(screenshots.columns) == ["id_user", "file", "t_epoch_ts_ms", "state"]
    assert list(screenshots["file"]) == ["a.jpg", "b.jpg"]
    assert screenshots["state"].isna().tolist() == [True, False]
    accessibility = pd.read_csv(os.path.join(metadata, "app_accessibility_data-consolidated.csv"))
    assert "text" not in accessibility.columns
    assert os.path.isfile(os.path.join(metadata, "package_event_data-consolidated.csv"))
    assert os.path.isfile(os.path.join(metadata, "manifest-consolidated.csv"))


def test_combine_keeps_ocr_by_default(tmp_path):
    files = [write(tmp_path / "u" / "screenshot_data_csv_1.csv", "id_user,text\n1,hello\n")]
    query_id = str(tmp_path / "q")

    cc.combine_csv_files({"hash1": files}, query_id)

    out = pd.read_csv(os.path.join(query_id, "combined", "panelists", "hash1", "metadata",
                                   "screenshot_data-consolidated.csv"))
    assert list(out["text"]) == ["hello"]


@pytest.mark.skipif(cc.cv2 is None, reason="opencv-python not installed")
def test_unzip_redacts_images_into_output_and_skips_unreadable_ones(tmp_path):
    source = tmp_path / "src"
    source.mkdir()
    cc.cv2.imwrite(str(source / "shot.png"), np.full((40, 40, 3), 255, dtype=np.uint8))
    (source / "broken.jpg").write_bytes(b"not an image")
    zip_path = tmp_path / "image_zip_1_2.zip"
    with zipfile.ZipFile(zip_path, "w") as z:
        z.write(source / "shot.png", "shot.png")
        z.write(source / "broken.jpg", "broken.jpg")
    unzipped, images = tmp_path / "unzipped", tmp_path / "images"
    unzipped.mkdir()
    images.mkdir()

    cc.unzip_file(str(zip_path), str(unzipped), str(images))

    assert sorted(os.listdir(images)) == ["shot.png"]


def test_redaction_check_rejects_opencv_without_cascade_classifier(monkeypatch):
    class OpenCv5:
        __version__ = "5.0.0"

    monkeypatch.setattr(cc, "cv2", OpenCv5())

    with pytest.raises(RuntimeError, match="CascadeClassifier"):
        cc.check_face_redaction_available()


def test_unzip_does_not_copy_images_without_opencv(tmp_path, monkeypatch):
    monkeypatch.setattr(cc, "cv2", None)
    source = write(tmp_path / "src" / "shot.png", "x")
    zip_path = tmp_path / "image_zip_1_1.zip"
    with zipfile.ZipFile(zip_path, "w") as z:
        z.write(source, "shot.png")
    images = tmp_path / "images"
    images.mkdir()

    cc.unzip_file(str(zip_path), str(tmp_path / "unzipped"), str(images))

    assert os.listdir(images) == []


def make_screenshot_csv(query_id, panelist, rows, with_state):
    header = "id_user,file,t_natural_day_ts" + (",state" if with_state else "")
    lines = [header] + [",".join(r if with_state else r[:3]) for r in rows]
    write(os.path.join(query_id, "combined", "panelists", panelist, "metadata",
                       "screenshot_data-consolidated.csv"), "\n".join(lines) + "\n")


def test_daily_stats_without_state_column_writes_volume_summaries_only(tmp_path):
    query_id = str(tmp_path / "q")
    make_screenshot_csv(query_id, "h1", [
        ("1", "a.jpg", "2026-07-01"), ("1", "b.jpg", "2026-07-01"), ("1", "c.jpg", "2026-07-02"),
    ], with_state=False)

    summary = cc.calculate_screenshot_daily_stats(query_id)

    files = sorted(os.listdir(os.path.join(query_id, "combined", "summary")))
    assert files == ["images_per_day.csv", "images_per_participant_day.csv",
                     "overall_statistics.csv", "participant_statistics.csv"]
    assert summary["participant_days"] == 2
    assert summary["total_images"] == 3
    assert "total_normal_screenshots" not in summary


def test_daily_stats_with_state_flags_three_days_without_normal(tmp_path):
    query_id = str(tmp_path / "q")
    make_screenshot_csv(query_id, "h1", [
        ("1", "a.jpg", "2026-07-01", "Normal"),
        ("1", "b.jpg", "2026-07-02", "ScreenLocked"),
        ("1", "c.jpg", "2026-07-03", "ScreenLocked"),
        ("1", "d.jpg", "2026-07-04", "ScreenLocked"),
        ("1", "e.jpg", "2026-07-05", "Normal"),
    ], with_state=True)

    summary = cc.calculate_screenshot_daily_stats(query_id)

    runs = pd.read_csv(os.path.join(query_id, "combined", "summary",
                                    "participants_without_normal_3_consecutive_days.csv"))
    assert runs.to_dict("records") == [{"id_user": 1, "start_date": "2026-07-02",
                                        "end_date": "2026-07-04", "consecutive_days_without_normal": 3}]
    assert summary["total_normal_screenshots"] == 2
    participants = pd.read_csv(os.path.join(query_id, "combined", "summary", "participant_statistics.csv"))
    assert participants["longest_consecutive_days_without_normal"].tolist() == [3]


def test_pipeline_after_download_unzips_every_panelist_and_consolidates(tmp_path, monkeypatch):
    # Mirrors the S3 key layout under <query_id>/zipped and runs the real unzip processes.
    monkeypatch.chdir(tmp_path)
    query_id = "query_test"
    panelist_count = os.cpu_count() + 2  # more panelists than unzip processes
    for i in range(panelist_count):
        folder = os.path.join(query_id, "zipped", "tenant", "1_t", "panel", "2", "V_1", "panelist", f"hash{i}")
        os.makedirs(folder)
        with zipfile.ZipFile(os.path.join(folder, f"csv_zip_{i}.zip"), "w") as z:
            z.writestr(f"screenshot_data_csv_{i}.csv", f"id_user,file,t_natural_day_ts\n{i},a.jpg,2026-07-01\n")
            z.writestr(f"package_event_data_csv_{i}.csv", f"id_user,apk\n{i},com.a\n")

    cc.process_child_folder_and_unzip_async(query_id)
    cc.combine_csv_files(cc.process_child_folders_csvs(os.path.join(query_id, "unzipped")), query_id)
    summary = cc.calculate_screenshot_daily_stats(query_id)

    panelists = os.listdir(os.path.join(query_id, "combined", "panelists"))
    assert sorted(panelists) == sorted(f"hash{i}" for i in range(panelist_count))
    assert summary["participants"] == panelist_count
