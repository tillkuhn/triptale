output "bucket_name" {
  value = aws_s3_bucket.backpack.bucket
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

output "cdn_base_url" {
  description = "Public base URL of backpack images (maps to attachments/ in the bucket); null without enable_cdn."
  value       = var.enable_cdn ? "https://${aws_cloudfront_distribution.backpack[0].domain_name}/" : null
}
