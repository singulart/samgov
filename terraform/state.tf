provider "aws" {
  region = "us-east-1"
  default_tags {
    tags = {
      Service     = "BetterSAM"
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