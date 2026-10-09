variable "aws_region" {
  description = "Region of the backpack bucket, e.g. eu-central-1."
  type        = string
}

variable "aws_profile" {
  description = "Admin profile from ~/.aws/credentials used for provisioning and state access (null = default credential chain)."
  type        = string
  default     = null
}

variable "app" {
  description = "App name; prefixes the bucket (<app>-backpack) and the IAM user (<app>-app)."
  type        = string
  default     = "triptale"
}

variable "state_bucket" {
  description = "Existing S3 bucket holding the OpenTofu state (key <app>/terraform.tfstate, same region as aws_region)."
  type        = string
}
