# Attachments bucket (todo 33) plus an IAM user whose access key can touch only this bucket.

resource "aws_s3_bucket" "attachments" {
  bucket = "${var.app}-attachments"

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_public_access_block" "attachments" {
  bucket = aws_s3_bucket.attachments.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# SSE-S3, so the app user needs no KMS permissions.
resource "aws_s3_bucket_server_side_encryption_configuration" "attachments" {
  bucket = aws_s3_bucket.attachments.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_iam_user" "app" {
  name = "${var.app}-app"
}

data "aws_iam_policy_document" "app" {
  statement {
    sid = "ListBucket"
    actions = [
      "s3:ListBucket",
      "s3:ListBucketMultipartUploads",
      "s3:GetBucketLocation",
    ]
    resources = [aws_s3_bucket.attachments.arn]
  }

  statement {
    sid = "ObjectReadWrite"
    actions = [
      "s3:GetObject",
      "s3:PutObject",
      "s3:DeleteObject",
      "s3:AbortMultipartUpload",
      "s3:ListMultipartUploadParts",
    ]
    resources = ["${aws_s3_bucket.attachments.arn}/*"]
  }
}

resource "aws_iam_user_policy" "app" {
  name   = "${var.app}-bucket-access"
  user   = aws_iam_user.app.name
  policy = data.aws_iam_policy_document.app.json
}

# The secret ends up in the local terraform.tfstate (gitignored) — rotate by tainting this resource.
resource "aws_iam_access_key" "app" {
  user = aws_iam_user.app.name
}
