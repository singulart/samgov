import boto3
from boto3.dynamodb.conditions import Attr

dynamodb = boto3.resource("dynamodb")
table = dynamodb.Table("samgov")

def count_unique_emails():
    unique = set()
    scan_kwargs = {
        # Fetch only the email attribute to reduce read cost
        "ProjectionExpression": "#e",
        "ExpressionAttributeNames": {"#e": "email"},
        # Optional: only count items where email exists and is non-empty
        "FilterExpression": Attr("email").exists()
    }

    last_key = None
    while True:
        if last_key:
            scan_kwargs["ExclusiveStartKey"] = last_key

        resp = table.scan(**scan_kwargs)

        for item in resp.get("Items", []):
            e = item.get("email")
            if isinstance(e, str) and e.strip():
                unique.add(e.strip().lower())  # normalize if desired

        last_key = resp.get("LastEvaluatedKey")
        if not last_key:
            break

    return len(unique)

print("Unique emails:", count_unique_emails())
