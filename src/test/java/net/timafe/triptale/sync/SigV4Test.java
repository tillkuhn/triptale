package net.timafe.triptale.sync;

import org.junit.jupiter.api.Test;

import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** Example requests and signatures from the AWS S3 SigV4 header-based auth documentation. */
class SigV4Test {

    private static final String KEY_ID = "AKIAIOSFODNN7EXAMPLE";
    private static final String SECRET = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY";
    private static final String DATE = "20130524T000000Z";

    @Test
    void authorization_getObjectExample() {
        SortedMap<String, String> headers = new TreeMap<>();
        headers.put("host", "examplebucket.s3.amazonaws.com");
        headers.put("range", "bytes=0-9");
        headers.put("x-amz-content-sha256", SigV4.EMPTY_SHA256);
        headers.put("x-amz-date", DATE);

        String auth = SigV4.authorization("GET", "/test.txt", "", headers, SigV4.EMPTY_SHA256,
                KEY_ID, SECRET, "us-east-1", DATE);

        assertEquals("AWS4-HMAC-SHA256 Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request,"
                + "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date,"
                + "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41", auth);
    }

    @Test
    void authorization_listObjectsExample() {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("max-keys", "2");
        params.put("prefix", "J");
        SortedMap<String, String> headers = new TreeMap<>();
        headers.put("host", "examplebucket.s3.amazonaws.com");
        headers.put("x-amz-content-sha256", SigV4.EMPTY_SHA256);
        headers.put("x-amz-date", DATE);

        String auth = SigV4.authorization("GET", "/", SigV4.canonicalQuery(params), headers, SigV4.EMPTY_SHA256,
                KEY_ID, SECRET, "us-east-1", DATE);

        assertTrue(auth.endsWith("Signature=34b48302e7b5fa45bde8084f4b7868a86f0a534bc59db6670ed5711ef69dc6f7"), auth);
    }

    @Test
    void uriEncode_keepsUnreservedAndOptionallySlash() {
        assertEquals("attachments/2026/a%20b/%C3%BC~x_y-z.jpg", SigV4.uriEncode("attachments/2026/a b/ü~x_y-z.jpg", false));
        assertEquals("attachments%2F", SigV4.uriEncode("attachments/", true));
    }

    @Test
    void canonicalQuery_encodesAndKeepsOrder() {
        SortedMap<String, String> params = new TreeMap<>();
        params.put("prefix", "p/attachments/");
        params.put("list-type", "2");
        assertEquals("list-type=2&prefix=p%2Fattachments%2F", SigV4.canonicalQuery(params));
    }
}
