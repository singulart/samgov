locals {
  schedule_expression = "rate(30 minutes)"
  function_version    = "0.4.1-SNAPSHOT"
}

data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "argorand_lambdas_repository" {
  bucket = "argorand-lambdas-repository"
}

resource "aws_s3_bucket_lifecycle_configuration" "argorand_lambdas_repository_lifecycle" {
  bucket = aws_s3_bucket.argorand_lambdas_repository.id

  rule {
    id     = "NoncurrentVersionExpirationRule"
    status = "Enabled"
    noncurrent_version_expiration {
      noncurrent_days = 30
    }
  }

  rule {
    id     = "abort-incomplete-multipart-upload"
    status = "Enabled"

    abort_incomplete_multipart_upload {
      days_after_initiation = 2
    }
  }

  rule {
    id     = "transition-and-expiration-rule"
    status = "Enabled"

    transition {
      days          = 30 # Transition objects to another storage class after 30 days
      storage_class = "STANDARD_IA" # Change to desired storage class (e.g., STANDARD_IA, GLACIER, etc.)
    }

    expiration {
      days = 365 # Expire (delete) objects after 365 days
    }
  }  
}

resource "aws_s3_bucket_versioning" "argorand_lambdas_repository_versioning" {
  bucket = aws_s3_bucket.argorand_lambdas_repository.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "argorand_lambdas_repository_encryption" {
  bucket = aws_s3_bucket.argorand_lambdas_repository.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_lambda_function" "samgov_notifier" {
  function_name = "samgov_notifier"
  runtime       = "java25"
  handler       = "org.springframework.cloud.function.adapter.aws.FunctionInvoker"
  role          = aws_iam_role.lambda_execution_role.arn
  architectures = [ "arm64" ]
  timeout       = 120
  memory_size   = 4096
  publish       = true

  tracing_config {
    mode = "Active"
  }


  // 2.12.2025 Disabled SnapStart because it slows down the Lambda Version creation significantly and does not bring any advantage 
  // because Lambda running every 30 minutes will have cold times anyways. 

  // https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html#snapstart-runtimes
  # snap_start {
  #   apply_on = "PublishedVersions"
  # }

  s3_bucket = aws_s3_bucket.argorand_lambdas_repository.bucket
  s3_key    = "samgov-${local.function_version}-aws.jar"

  environment {
    variables = {
      SAVED_QUERIES_TABLE    = aws_dynamodb_table.samgov-v2.name
      SPRING_PROFILES_ACTIVE = "prod"
      SES_SENDER             = "noreply@argorand.io"
      RANDOM_VAR             = "42873498237987"
    }
  }
}

resource "aws_dynamodb_table" "samgov" {
  name                        = "samgov"
  billing_mode                = "PROVISIONED"
  deletion_protection_enabled = "true"
  read_capacity               = 1
  write_capacity              = 1

  point_in_time_recovery {
    enabled = true
    recovery_period_in_days = 10
  }

  attribute {
    name = "lastProcessedAt"
    type = "S"
  }

  hash_key = "lastProcessedAt"

}

resource "aws_dynamodb_table" "samgov-v2" {
  name                        = "samgov-v2"
  billing_mode                = "PROVISIONED"
  deletion_protection_enabled = "true"
  read_capacity               = 1
  write_capacity              = 1

  point_in_time_recovery {
    enabled = true
    recovery_period_in_days = 10
  }

  attribute {
    name = "notificationId"
    type = "S"
  }

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "createdAt"
    type = "S"
  } 

  hash_key = "notificationId"

  global_secondary_index {
    name            = "gsi_userId_createdAt"
    projection_type = "ALL"
    read_capacity   = 1
    write_capacity  = 1

    key_schema {
      attribute_name = "userId"
      key_type       = "HASH"
    }
    key_schema {
      attribute_name = "createdAt"
      key_type       = "RANGE"
    }
  }
}

resource "aws_dynamodb_table" "samgov-users" {
  name                        = "samgov-users"
  billing_mode                = "PROVISIONED"
  deletion_protection_enabled = "true"
  read_capacity               = 1
  write_capacity              = 1

  point_in_time_recovery {
    enabled = true
    recovery_period_in_days = 10
  }

  attribute {
    name = "userId"
    type = "S"
  }

  attribute {
    name = "email"
    type = "S"
  } 

  hash_key = "userId"

  global_secondary_index {
    name            = "gsi_email"
    projection_type = "ALL"
    read_capacity   = 1
    write_capacity  = 1

    key_schema {
      attribute_name = "email"
      key_type       = "HASH"
    }
  }
}

resource "aws_cloudwatch_log_group" "samgov_function_logs" {
  name              = "/aws/lambda/samgov_notifier"
  retention_in_days = 14
}

resource "aws_iam_role" "lambda_execution_role" {
  name = "samgov_notifier_execution_role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement : [
      {
        Action = "sts:AssumeRole",
        Effect = "Allow",
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_policy" "lambda_permissions" {
  name        = "sam_gov_notifier_permissions"
  description = "Permissions for Lambda to access DynamoDB, SES, S3, and CloudWatch Logs"

  policy = jsonencode({
    Version = "2012-10-17",
    Statement : [
      {
        Action = [
          "dynamodb:GetItem",
          "dynamodb:Query",
          "dynamodb:Scan",
          "dynamodb:UpdateItem"
        ],
        Effect   = "Allow",
        Resource = [ 
          aws_dynamodb_table.samgov.arn,
          aws_dynamodb_table.samgov-v2.arn,
          aws_dynamodb_table.samgov-users.arn
        ]
      },
      {
        Action   = "ses:SendEmail",
        Effect   = "Allow",
        Resource = [
          "arn:aws:ses:us-east-1:${data.aws_caller_identity.current.account_id}:configuration-set/my-first-configuration-set",
          "arn:aws:ses:us-east-1:${data.aws_caller_identity.current.account_id}:identity/noreply@argorand.io",
          "arn:aws:ses:us-east-1:${data.aws_caller_identity.current.account_id}:identity/argorand.io"
        ]
      },
      {
        Action = [
          "s3:GetObject"
        ],
        Effect   = "Allow",
        Resource = "${aws_s3_bucket.argorand_lambdas_repository.arn}/*"
      },
      {
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ],
        Effect   = "Allow",
        Resource = "arn:aws:logs:us-east-1:*:log-group:/aws/lambda/samgov_notifier:*"
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "lambda_policy_attachment" {
  role       = aws_iam_role.lambda_execution_role.name
  policy_arn = aws_iam_policy.lambda_permissions.arn
}

resource "aws_cloudwatch_event_rule" "samgov_schedule" {
  name                = "samgov-schedule"
  schedule_expression = local.schedule_expression
}

resource "aws_cloudwatch_event_target" "samgov_target" {
  rule      = aws_cloudwatch_event_rule.samgov_schedule.name
  target_id = "samgov_notifier"
  arn       = aws_lambda_function.samgov_notifier.arn
}

resource "aws_lambda_permission" "allow_cloudwatch_to_invoke" {
  statement_id  = "AllowCloudWatchEventsToInvokeLambda"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.samgov_notifier.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.samgov_schedule.arn
}
