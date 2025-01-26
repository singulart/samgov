locals {
  schedule_expression = "rate(5 minutes)"
  function_version    = "0.1.0-SNAPSHOT"
}

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
  runtime       = "java21"
  handler       = "org.springframework.cloud.function.adapter.aws.FunctionInvoker"
  role          = aws_iam_role.lambda_execution_role.arn
  architectures = [ "arm64" ]
  timeout       = 120
  memory_size   = 512
  publish       = true

  tracing_config {
    mode = "Active"
  }

  // https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html#snapstart-runtimes
  snap_start {
    apply_on = "PublishedVersions"
  }

  s3_bucket = aws_s3_bucket.argorand_lambdas_repository.bucket
  s3_key    = "samgov-${local.function_version}-aws.jar"

  environment {
    variables = {
      SAVED_QUERIES_TABLE    = aws_dynamodb_table.samgov.name
      SPRING_PROFILES_ACTIVE = "prod"
    }
  }
}

resource "aws_dynamodb_table" "samgov" {
  name                        = "samgov"
  billing_mode                = "PROVISIONED"
  deletion_protection_enabled = "true"
  read_capacity               = 1
  write_capacity              = 1

  attribute {
    name = "lastProcessedAt"
    type = "S"
  }

  hash_key = "lastProcessedAt"

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
        Resource = aws_dynamodb_table.samgov.arn
      },
      {
        Action = [
          "dynamodb:ListTables" //TODO remove 
        ],
        Effect   = "Allow",
        Resource = "*"
      },
      {
        Action   = "ses:SendEmail",
        Effect   = "Allow",
        Resource = "arn:aws:ses:us-east-1:794689098735:identity/argorand.io"
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
