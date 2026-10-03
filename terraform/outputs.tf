output "bucket_name" {
  value = aws_s3_bucket.attachments.bucket
}

output "region" {
  value = var.aws_region
}

output "endpoint" {
  value = "https://s3.${var.aws_region}.amazonaws.com"
}

output "access_key_id" {
  value = aws_iam_access_key.app.id
}

output "secret_access_key" {
  value     = aws_iam_access_key.app.secret
  sensitive = true
}
