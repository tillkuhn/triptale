# terraform (OpenTofu)

Creates the S3 bucket for trip attachments (todo 33, `docs/33_object_storage_for_trip_attachments.md`)
(`<app>-backpack`, `app` defaults to `triptale`) and a dedicated IAM user `<app>-app` whose access key can only read/write that bucket.

```bash
cp triptale.auto.tfvars.example triptale.auto.tfvars   # fill in region, admin profile, state bucket
make plan
make apply
make key       # access key ID + secret for the TripTale settings
```

Run `make` for all targets.

Run `apply` with your admin profile (`aws_profile`); the app itself only ever gets the
`<app>-app` key.

**State lives in S3** (`<state_bucket>/<app>/terraform.tfstate`, encrypted, S3 native
locking) **and contains the secret access key** — restrict access to the state bucket.

Rotate the key: `tofu apply -replace=aws_iam_access_key.app`.

**Optional CDN** (`enable_cdn = true`, `cdn.tf`, todo 44): a CloudFront distribution serves backpack
images publicly over HTTPS on its free `d….cloudfront.net` hostname, for hotlinking from blog
posts. The bucket stays private (Origin Access Control). Only image files (`jpg`, `jpeg`, `png`,
`webp`) below `attachments/` are readable; the origin path maps the CDN root to `attachments/`, so
`https://<cdn>/2026/<slug>/<day>/<file>.jpg` is `attachments/2026/<slug>/<day>/<file>.jpg` in the
bucket. `make output` shows `cdn_base_url`. Set `enable_cdn = false` and apply to remove it again.
