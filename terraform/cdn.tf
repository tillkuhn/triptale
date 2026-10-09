# Optional CloudFront distribution serving backpack images publicly (todo 44, docs/done/44_wordpress_export.md).
# The bucket stays private: CloudFront reads through Origin Access Control, and only images below
# attachments/ — radio/, backups etc. are neither addressable (origin path) nor readable (bucket policy).

locals {
  cdn_image_extensions = ["jpg", "jpeg", "png", "webp"]
}

resource "aws_cloudfront_origin_access_control" "backpack" {
  count = var.enable_cdn ? 1 : 0

  name                              = "${var.app}-backpack"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  count = var.enable_cdn ? 1 : 0

  name = "Managed-CachingOptimized"
}

resource "aws_cloudfront_distribution" "backpack" {
  count = var.enable_cdn ? 1 : 0

  enabled         = true
  comment         = "${var.app} backpack images"
  price_class     = "PriceClass_100" # Europe + North America, the cheapest class
  is_ipv6_enabled = true
  http_version    = "http2and3"

  origin {
    origin_id                = "backpack"
    domain_name              = aws_s3_bucket.backpack.bucket_regional_domain_name
    origin_path              = "/attachments"
    origin_access_control_id = aws_cloudfront_origin_access_control.backpack[0].id
  }

  default_cache_behavior {
    target_origin_id       = "backpack"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized[0].id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # The free *.cloudfront.net hostname with its managed certificate, no custom domain
  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

# Only this distribution, only GetObject, only image extensions (both cases) below attachments/.
# Without ListBucket, missing and forbidden keys both answer 403.
data "aws_iam_policy_document" "cdn_read" {
  count = var.enable_cdn ? 1 : 0

  statement {
    sid     = "CloudFrontReadImages"
    actions = ["s3:GetObject"]
    resources = flatten([
      for ext in local.cdn_image_extensions : [
        "${aws_s3_bucket.backpack.arn}/attachments/*.${ext}",
        "${aws_s3_bucket.backpack.arn}/attachments/*.${upper(ext)}",
      ]
    ])

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.backpack[0].arn]
    }
  }
}

resource "aws_s3_bucket_policy" "backpack" {
  count = var.enable_cdn ? 1 : 0

  bucket = aws_s3_bucket.backpack.id
  policy = data.aws_iam_policy_document.cdn_read[0].json

  # The policy is a service-principal grant with a SourceArn condition, which the public access
  # block does not count as public; still apply it only once the block is in place.
  depends_on = [aws_s3_bucket_public_access_block.backpack]
}
