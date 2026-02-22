#!/usr/bin/env python3
import argparse
import json
import logging
import os
import sys
import time
import uuid
from typing import Any, Dict, Optional, Tuple

import boto3
from boto3.dynamodb.conditions import Key
from botocore.config import Config
from botocore.exceptions import ClientError

LOG = logging.getLogger("samgov-v2-migrate")


def find_user_id_by_email_via_gsi(
    users_table,
    email: str,
    email_gsi_name: str,
    max_retries: int = 8,
) -> Optional[str]:
    """
    Query samgov-users by email using the specified GSI.
    Returns userId if found, else None.

    NOTE: GSIs do not support ConsistentRead=True.
    """
    if not email:
        return None

    for attempt in range(max_retries):
        try:
            resp = users_table.query(
                IndexName=email_gsi_name,
                KeyConditionExpression=Key("email").eq(email),
                Limit=1,
                ConsistentRead=False,
            )
            items = resp.get("Items", [])
            if not items:
                return None
            return items[0].get("userId")
        except ClientError as e:
            code = e.response.get("Error", {}).get("Code", "")
            if code in ("ProvisionedThroughputExceededException", "ThrottlingException", "RequestLimitExceeded"):
                time.sleep(min(2 ** attempt * 0.2, 5.0))
                continue
            raise


def write_v2_item(
    dest_table,
    item: Dict[str, Any],
    user_id: str,
    dry_run: bool,
    max_retries: int = 8,
) -> None:
    """
    Create v2 record (version=2 + userId) and put into destination table.
    Retries on throttling.
    """
    v2_item: Dict[str, Any] = dict(item)
    v2_item["version"] = 2
    v2_item["userId"] = user_id
    v2_item["notificationId"] = str(uuid.uuid4())

    if dry_run:
        return

    for attempt in range(max_retries):
        try:
            LOG.debug("put_item attempt %d (notificationId=%s)", attempt + 1, v2_item.get("notificationId"))
            dest_table.put_item(Item=v2_item)
            return
        except ClientError as e:
            code = e.response.get("Error", {}).get("Code", "")
            if code in ("ProvisionedThroughputExceededException", "ThrottlingException", "RequestLimitExceeded"):
                time.sleep(min(2 ** attempt * 0.2, 5.0))
                continue
            raise


def migrate_page(
    source_table,
    dest_table,
    users_table,
    *,
    email_attr: str,
    email_gsi_name: str,
    dest_pk_attr: str,
    dry_run: bool,
    scan_limit: Optional[int],
    exclusive_start_key: Optional[Dict[str, Any]],
) -> Tuple[int, int, int, Optional[Dict[str, Any]]]:
    """
    Migrate a single Scan "page" from source->dest.

    For each source item:
      - read email
      - lookup userId from samgov-users using email GSI
      - write to samgov-v2 with version=2 and userId, all other attrs unchanged

    Returns: (written, skipped, failed, last_evaluated_key)
    """
    written = 0
    skipped = 0
    failed = 0

    scan_kwargs: Dict[str, Any] = {}
    if scan_limit:
        scan_kwargs["Limit"] = scan_limit
    if exclusive_start_key:
        scan_kwargs["ExclusiveStartKey"] = exclusive_start_key

    LOG.debug("Scanning source table...")
    resp = source_table.scan(**scan_kwargs) if scan_kwargs else source_table.scan()
    items = resp.get("Items", [])
    last_evaluated_key = resp.get("LastEvaluatedKey")
    LOG.debug("Scanned %d items, processing...", len(items))

    for i, item in enumerate(items):
        try:

            email_val = item.get(email_attr)
            if not isinstance(email_val, str) or not email_val.strip():
                skipped += 1
                continue
            email = email_val.strip()

            LOG.debug("Item %d/%d: looking up userId for email=%r", i + 1, len(items), email)
            user_id = find_user_id_by_email_via_gsi(users_table, email, email_gsi_name)
            if not user_id:
                skipped += 1
                LOG.warning(
                    "No userId found for email=%r; skipping item %s=%r",
                    email,
                    dest_pk_attr,
                    item.get(dest_pk_attr),
                )
                continue

            LOG.debug("Item %d/%d: writing to dest", i + 1, len(items))
            write_v2_item(dest_table, item, user_id, dry_run=dry_run)
            written += 1

        except Exception:
            failed += 1
            LOG.exception("Failed migrating item %s=%r", dest_pk_attr, item.get(dest_pk_attr))

    return written, skipped, failed, last_evaluated_key


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Scan samgov and migrate to samgov-v2 (add version=2 and userId looked up from samgov-users by email GSI)."
    )
    parser.add_argument("--region", default=os.getenv("AWS_REGION") or os.getenv("AWS_DEFAULT_REGION"),
                        help="AWS region (defaults to AWS_REGION/AWS_DEFAULT_REGION env var)")
    parser.add_argument("--endpoint-url", default=os.getenv("AWS_ENDPOINT_URL"),
                        help="DynamoDB endpoint URL (e.g. http://localhost:4566 for Localstack)")
    parser.add_argument("--source-table", default="samgov", help="Source table name (default: samgov)")
    parser.add_argument("--dest-table", default="samgov-v2", help="Destination table name (default: samgov-v2)")
    parser.add_argument("--users-table", default="samgov-users", help="Users table name (default: samgov-users)")
    parser.add_argument("--email-attr", default="email", help="Email attribute in source table items (default: email)")
    parser.add_argument("--users-email-gsi", default="gsi_email", help="GSI name on samgov-users for querying by email")
    parser.add_argument("--dest-pk-attr", default="notificationId",
                        help="Partition key attribute name expected in destination table items (default: notificationId)")
    parser.add_argument("--scan-limit", type=int, default=None,
                        help="DynamoDB Scan Limit per request (smaller = safer for throttling/tests)")
    parser.add_argument("--resume-from", default=None,
                        help="JSON ExclusiveStartKey to resume from (output from logs/checkpoint file)")
    parser.add_argument("--checkpoint-file", default=None,
                        help="Optional file path to persist LastEvaluatedKey as JSON after each page")
    parser.add_argument("--dry-run", action="store_true", help="Do not write to destination; just count")
    parser.add_argument("--log-level", default="INFO", help="DEBUG, INFO, WARNING, ERROR (default: INFO)")
    args = parser.parse_args()

    logging.basicConfig(
        level=getattr(logging, args.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        force=True,
    )

    LOG.info("Starting migration: source=%s dest=%s users=%s region=%s endpoint=%s",
             args.source_table, args.dest_table, args.users_table, args.region, args.endpoint_url or "default")

    if not args.region:
        LOG.error("Region not set. Use --region or set AWS_REGION/AWS_DEFAULT_REGION.")
        return 2

    ddb_config = Config(connect_timeout=10, read_timeout=60, retries={"mode": "standard", "max_attempts": 3})
    ddb_kwargs = {"region_name": args.region, "config": ddb_config}
    if args.endpoint_url:
        ddb_kwargs["endpoint_url"] = args.endpoint_url
    ddb = boto3.resource("dynamodb", **ddb_kwargs)
    source = ddb.Table(args.source_table)
    dest = ddb.Table(args.dest_table)
    users = ddb.Table(args.users_table)

    exclusive_start_key: Optional[Dict[str, Any]] = None
    if args.resume_from:
        exclusive_start_key = json.loads(args.resume_from)

    written_total = 0
    skipped_total = 0
    failed_total = 0
    pages = 0

    while True:
        pages += 1
        written, skipped, failed, last_key = migrate_page(
            source,
            dest,
            users,
            email_attr=args.email_attr,
            email_gsi_name=args.users_email_gsi,
            dest_pk_attr=args.dest_pk_attr,
            dry_run=args.dry_run,
            scan_limit=args.scan_limit,
            exclusive_start_key=exclusive_start_key,
        )

        written_total += written
        skipped_total += skipped
        failed_total += failed

        LOG.info(
            "Page %d done: written=%d skipped=%d failed=%d | totals: written=%d skipped=%d failed=%d | last_key=%s",
            pages,
            written,
            skipped,
            failed,
            written_total,
            skipped_total,
            failed_total,
            "yes" if last_key else "no",
        )

        if args.checkpoint_file:
            try:
                with open(args.checkpoint_file, "w", encoding="utf-8") as f:
                    json.dump({"LastEvaluatedKey": last_key}, f)
            except Exception:
                LOG.exception("Failed writing checkpoint file %s", args.checkpoint_file)

        if not last_key:
            break
        exclusive_start_key = last_key

    LOG.info("DONE. pages=%d written=%d skipped=%d failed=%d", pages, written_total, skipped_total, failed_total)
    return 0 if failed_total == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
