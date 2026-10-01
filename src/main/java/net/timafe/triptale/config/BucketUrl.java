package net.timafe.triptale.config;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * An attachment storage location in {@code s3://bucket} or {@code s3://bucket/prefix} form.
 * {@code prefix} has no leading/trailing slashes and is empty when the URL names just the bucket.
 */
public record BucketUrl(String bucket, String prefix) {

    private static final String SCHEME = "s3://";
    // S3 bucket naming rules: 3–63 chars, lowercase letters, digits, dots, hyphens, alnum at both ends
    private static final Pattern BUCKET = Pattern.compile("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]");

    /**
     * Parses {@code s3://bucket[/prefix]}; the scheme is case-insensitive, surrounding and
     * repeated trailing slashes of the prefix are dropped.
     *
     * @throws IllegalArgumentException with a user-facing message if the URL is malformed
     */
    public static BucketUrl parse(String url) {
        String text = url == null ? "" : url.trim();
        if (!text.toLowerCase(Locale.ROOT).startsWith(SCHEME)) {
            throw new IllegalArgumentException("Bucket URL must start with " + SCHEME);
        }
        String rest = text.substring(SCHEME.length());
        int slash = rest.indexOf('/');
        String bucket = slash < 0 ? rest : rest.substring(0, slash);
        String prefix = slash < 0 ? "" : rest.substring(slash + 1).replaceAll("^/+|/+$", "");
        if (!BUCKET.matcher(bucket).matches()) {
            throw new IllegalArgumentException("Invalid bucket name '" + bucket
                    + "' (3–63 chars: lowercase letters, digits, dots, hyphens)");
        }
        return new BucketUrl(bucket, prefix);
    }
}
