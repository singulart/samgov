#!/usr/bin/env python3
import argparse
import logging
import os
import sys
import uuid
from typing import Optional, Set

import boto3
from botocore.exceptions import ClientError

LOG = logging.getLogger("samgov")


def scan_unique_emails(
    table,
    email_attr: str = "email",
    page_limit: Optional[int] = None,
) -> Set[str]:
    """
    Sequentially scan a DynamoDB table and collect unique, non-empty email values.
    Uses ProjectionExpression to minimize read costs.
    """
    emails: Set[str] = set()
    last_evaluated_key = None
    pages = 0

    # NOTE: "email" is not a reserved word, but aliasing avoids surprises.
    expr_attr_names = {"#e": email_attr}

    while True:
        kwargs = {
            "ProjectionExpression": "#e",
            "ExpressionAttributeNames": expr_attr_names,
        }
        if last_evaluated_key:
            kwargs["ExclusiveStartKey"] = last_evaluated_key

        resp = table.scan(**kwargs)
        pages += 1

        for item in resp.get("Items", []):
            val = item.get(email_attr)
            if isinstance(val, str):
                email = val.strip()
                if email:
                    emails.add(email)

        last_evaluated_key = resp.get("LastEvaluatedKey")
        LOG.info("Scanned page %d, unique emails so far: %d", pages, len(emails))

        if page_limit and pages >= page_limit:
            LOG.warning("Stopping early due to --page-limit=%d", page_limit)
            break

        if not last_evaluated_key:
            break

    return emails


def put_user_if_absent(users_table, email: str) -> bool:
    """
    Insert a user record keyed by email, only if it doesn't already exist.
    Returns True if inserted, False if it already existed.
    """
    user_id = str(uuid.uuid4())
    item = {"email": email, "userId": user_id}

    try:
        users_table.put_item(
            Item=item,
            ConditionExpression="attribute_not_exists(#e)",
            ExpressionAttributeNames={"#e": "email"},
        )
        return True
    except ClientError as e:
        code = e.response.get("Error", {}).get("Code")
        if code == "ConditionalCheckFailedException":
            return False
        raise


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Scan samgov table for unique emails and upsert into samgov-users."
    )
    parser.add_argument("--source-table", default="samgov", help="Source table name (default: samgov)")
    parser.add_argument("--dest-table", default="samgov-users", help="Destination table name (default: samgov-users)")
    parser.add_argument("--email-attr", default="email", help="Email attribute name in source items (default: email)")
    parser.add_argument("--region", default=os.getenv("AWS_REGION") or os.getenv("AWS_DEFAULT_REGION"),
                        help="AWS region (defaults to AWS_REGION/AWS_DEFAULT_REGION env var)")
    parser.add_argument("--page-limit", type=int, default=None,
                        help="For testing: stop after scanning N pages")
    parser.add_argument("--dry-run", action="store_true",
                        help="Do not write to destination; just report counts")
    parser.add_argument("--log-level", default="INFO", help="DEBUG, INFO, WARNING, ERROR (default: INFO)")
    args = parser.parse_args()

    logging.basicConfig(level=getattr(logging, args.log_level.upper(), logging.INFO),
                        format="%(asctime)s %(levelname)s %(name)s: %(message)s")

    if not args.region:
        LOG.error("Region not set. Use --region or set AWS_REGION/AWS_DEFAULT_REGION.")
        return 2

    ddb = boto3.resource("dynamodb", region_name=args.region)
    source = ddb.Table(args.source_table)
    dest = ddb.Table(args.dest_table)

    LOG.info("Scanning source table %s for unique '%s' values...", args.source_table, args.email_attr)
    emails = scan_unique_emails(source, email_attr=args.email_attr, page_limit=args.page_limit)

    LOG.info("Total unique emails found: %d", len(emails))
    if args.dry_run:
        LOG.info("Dry run enabled; not writing to %s.", args.dest_table)
        return 0

    inserted = 0
    existed = 0
    failed = 0

    for i, email in enumerate(sorted(emails), start=1):
        try:
            did_insert = put_user_if_absent(dest, email)
            if did_insert:
                inserted += 1
            else:
                existed += 1

            if i % 100 == 0:
                LOG.info("Processed %d/%d emails (inserted=%d, existed=%d, failed=%d)",
                         i, len(emails), inserted, existed, failed)
        except Exception:
            failed += 1
            LOG.exception("Failed to write user for email=%r", email)

    LOG.info("Done. inserted=%d existed=%d failed=%d total=%d",
             inserted, existed, failed, len(emails))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
