terraform {
  required_version = ">= 1.10" # OpenTofu: variables in backend (1.8), S3 native locking (1.10)

  backend "s3" {
    bucket       = var.state_bucket
    key          = "${var.app}/terraform.tfstate"
    region       = var.aws_region
    profile      = var.aws_profile
    encrypt      = true
    use_lockfile = true
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region  = var.aws_region
  profile = var.aws_profile

  default_tags {
    tags = {
      app       = var.app
      managedBy = "terraform"
    }
  }
}
