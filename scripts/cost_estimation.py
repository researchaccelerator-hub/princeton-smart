"""
Estimates monthly S3 cost for a study, comparing:

  Variant 1: one combined ZIP per batch (CSVs and images), all downloaded daily.
  Variant 2: separate CSV and image ZIPs per batch (the app's current layout), with only
             the CSV ZIPs downloaded daily.

Rates default to S3 Standard list prices (us-east-1). Check current AWS pricing and your
own image sizes before relying on the numbers.

Usage:
    python cost_estimation.py --participants 150 --images-per-day 4000
"""
import argparse


def estimate_s3_costs(
    num_participants=150,
    images_per_participant_per_day=4000,
    images_per_zip=50,
    avg_image_size_mb=0.5,       # Adjust based on your actual average image size
    avg_metadata_size_kb=10.0,   # Adjust based on your actual metadata size per image
    days_in_month=30,
    retention_days=30,           # 30 means data accumulates all month. 1 means deleted daily via lifecycle.
    storage_rate_per_gb=0.023,   # S3 Standard Tier
    put_rate_per_1000=0.005,     # PUT, COPY, POST
    get_rate_per_1000=0.0004,    # GET and all other requests
    egress_rate_per_gb=0.09,     # Data Transfer Out to Internet
    free_egress_gb=100           # AWS free tier for egress, shared across the whole account
):
    """
    Returns a dict of monthly cost components for both variants.
    """
    # Conversions
    mb_to_gb = 1 / 1024
    kb_to_gb = 1 / (1024 * 1024)

    image_size_gb = avg_image_size_mb * mb_to_gb
    metadata_size_gb = avg_metadata_size_kb * kb_to_gb

    # Daily scale
    total_images_per_day = num_participants * images_per_participant_per_day
    batches_per_day = total_images_per_day / images_per_zip

    daily_image_gb = total_images_per_day * image_size_gb
    daily_metadata_gb = total_images_per_day * metadata_size_gb
    daily_total_gb = daily_image_gb + daily_metadata_gb

    # Storage volume at rest is the same for both variants.
    if retention_days >= days_in_month:
        avg_storage_gb = daily_total_gb * (days_in_month + 1) / 2  # linear accumulation over the month
    else:
        avg_storage_gb = daily_total_gb * retention_days
    storage_cost = avg_storage_gb * storage_rate_per_gb

    def variant(puts_per_batch, egress_gb_per_day):
        monthly_puts = batches_per_day * puts_per_batch * days_in_month
        monthly_gets = batches_per_day * days_in_month  # one ZIP downloaded per batch in both variants
        request_cost = (monthly_puts / 1000 * put_rate_per_1000) + (monthly_gets / 1000 * get_rate_per_1000)
        monthly_egress_gb = egress_gb_per_day * days_in_month
        egress_cost = max(0, monthly_egress_gb - free_egress_gb) * egress_rate_per_gb
        return {
            "request_cost": request_cost,
            "storage_cost": storage_cost,
            "egress_cost": egress_cost,
            "egress_gb": monthly_egress_gb,
            "total": request_cost + storage_cost + egress_cost,
        }

    return {
        "days_in_month": days_in_month,
        "total_images_per_day": total_images_per_day,
        "daily_total_gb": daily_total_gb,
        "avg_storage_gb": avg_storage_gb,
        "combined": variant(puts_per_batch=1, egress_gb_per_day=daily_total_gb),
        "split": variant(puts_per_batch=2, egress_gb_per_day=daily_metadata_gb),
    }


def print_report(result):
    print(f"=== S3 Monthly Cost Estimation ({result['days_in_month']} Days) ===")
    print(f"Total Images Uploaded/Day: {result['total_images_per_day']:,}")
    print(f"Total Data Uploaded/Day:   {result['daily_total_gb']:.2f} GB (~{result['daily_total_gb'] / 1024:.2f} TB)")
    for title, key in [
        ("VARIANT 1: Combined Zips (Download Images + Metadata Daily)", "combined"),
        ("VARIANT 2: Separate Zips (Download Metadata Only Daily)", "split"),
    ]:
        v = result[key]
        print("-" * 50)
        print(title)
        print(f"  - Request Fees:  ${v['request_cost']:.2f}")
        print(f"  - Storage Fees:  ${v['storage_cost']:.2f} (Avg volume: {result['avg_storage_gb']:.2f} GB)")
        print(f"  - Egress Fees:   ${v['egress_cost']:.2f} ({v['egress_gb']:.2f} GB downloaded)")
        print(f"  * TOTAL: ${v['total']:.2f} / month")
    print("-" * 50)
    print(f"POTENTIAL MONTHLY SAVINGS WITH VARIANT 2: ${result['combined']['total'] - result['split']['total']:.2f}")


def main(argv=None):
    parser = argparse.ArgumentParser(description="Estimate monthly S3 cost for a study.")
    parser.add_argument("--participants", type=int, default=150)
    parser.add_argument("--images-per-day", type=int, default=4000, help="Images per participant per day")
    parser.add_argument("--images-per-zip", type=int, default=50)
    parser.add_argument("--image-mb", type=float, default=0.5, help="Average image size in MB")
    parser.add_argument("--metadata-kb", type=float, default=10.0, help="Average CSV data per image in KB")
    parser.add_argument("--days", type=int, default=30)
    parser.add_argument("--retention-days", type=int, default=30)
    args = parser.parse_args(argv)

    print_report(estimate_s3_costs(
        num_participants=args.participants,
        images_per_participant_per_day=args.images_per_day,
        images_per_zip=args.images_per_zip,
        avg_image_size_mb=args.image_mb,
        avg_metadata_size_kb=args.metadata_kb,
        days_in_month=args.days,
        retention_days=args.retention_days,
    ))


if __name__ == "__main__":
    main()
