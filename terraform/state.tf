provider "aws" {
  region = "us-east-1"
  default_tags {
    tags = {
      Service     = "lex-poc"
      Repo        = "samgov"
      Environment = "Prod"
    }
  }
}

terraform {
  backend "s3" {
    bucket = "terraform-state-argorand"
    region = "us-east-1"
    key = "personal/personal.tfstate"
  }
}