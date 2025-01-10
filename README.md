

# Local development with Localstack 

Default out-of-the-box configuration. Just start the localstack with `localstack start`.
It will listen for requests on port 4566. 

All requests to AWS API via `aws` CLI that need to be executed against Localstack must use `--endpoint-url http://localhost:4566`.

# AWS CLI commands


## Create Lambda function

```sh
aws lambda create-function \
  --function-name SamGovFunction \
  --timeout 120 \
  --runtime java21 \
  --role arn:aws:iam::000000000000:role/lambda-role \
  --handler org.springframework.cloud.function.adapter.aws.FunctionInvoker \
  --architectures arm64 \
  --zip-file fileb:///Users/lex/samgov/build/libs/samgov-0.0.1-SNAPSHOT-aws.jar \
  --endpoint-url=http://localhost:4566
```

## Invoke Lambda function

```sh
aws lambda invoke \
  --function-name SamGovFunction \
  --payload '{}' \
  --endpoint-url=http://localhost:4566 \
  output.json
```



## Create DynamoDB table

```sh
aws dynamodb create-table \
  --table-name samgov \
  --attribute-definitions AttributeName=email,AttributeType=S \
  --key-schema AttributeName=email,KeyType=HASH \
  --provisioned-throughput ReadCapacityUnits=1,WriteCapacityUnits=1 \
  --endpoint-url=http://localhost:4566
```

## View CloudWatch logs for Lamdba function

```sh
aws logs describe-log-streams \
    --log-group-name "/aws/lambda/SamGovFunction" \
    --order-by "LastEventTime" \
    --descending \
    --query "logStreams[*].logStreamName" \
    --output text \
  --endpoint-url=http://localhost:4566
```

