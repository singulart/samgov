provider "aws" {
  region = "us-east-1"
}

terraform {
  backend "s3" {
    bucket = "terraform-state-argorand"
    region = "us-east-1"
    key = "personal/personal.tfstate"
  }
}