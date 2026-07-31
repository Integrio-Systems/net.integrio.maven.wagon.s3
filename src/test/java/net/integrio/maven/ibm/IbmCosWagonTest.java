package net.integrio.maven.ibm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.integrio.maven.ibm.util.CosUtils;
import org.apache.maven.wagon.ConnectionException;
import org.apache.maven.wagon.authentication.AuthenticationInfo;
import org.apache.maven.wagon.repository.Repository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Drives the wagon against a stub of the object storage and of the IAM token service, which keeps
 * the requests that reach the wire assertable.
 */
final class IbmCosWagonTest {

    private static final String CONTENTS = "artifact contents";

    private static final String RESOURCE = "net/integrio/artifact-1.0.jar";

    private final List<String> requests = new ArrayList<>();

    private final List<String> authorizations = new ArrayList<>();

    private final AtomicInteger tokenRequests = new AtomicInteger();

    private HttpServer server;

    private String endpoint;

    private String tokenRequestBody;

    private byte[] uploaded;

    @TempDir
    private Path temporaryDirectory;

    @BeforeEach
    void startStorage() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/", this::handle);
        this.server.start();
        this.endpoint = "http://127.0.0.1:" + this.server.getAddress().getPort();
    }

    @AfterEach
    void stopStorage() {
        this.server.stop(0);
    }

    @Test
    void signsRequestsOfHmacCredentialsAndAddressesTheBucketByPath() throws Exception {
        IbmCosWagon wagon = wagon();
        wagon.connect(repository(), authentication("0123456789ABCDEFGHIJ", "secret-access-key"));

        assertTrue(wagon.resourceExists(RESOURCE));
        wagon.put(source(), RESOURCE);
        wagon.get(RESOURCE, destination());
        wagon.disconnect();

        assertEquals(CONTENTS, new String(this.uploaded, StandardCharsets.UTF_8));
        assertEquals(CONTENTS, new String(Files.readAllBytes(destination().toPath()), StandardCharsets.UTF_8));
        assertLinesMatch(List.of("HEAD /my-bucket/release/" + RESOURCE,
                "PUT /my-bucket/release/" + RESOURCE,
                "GET /my-bucket/release/" + RESOURCE), this.requests);
        this.authorizations.forEach(authorization -> {
            assertTrue(authorization.startsWith("AWS4-HMAC-SHA256 Credential=0123456789ABCDEFGHIJ/"), authorization);
            assertTrue(authorization.contains("/us-standard/s3/aws4_request"), authorization);
        });
    }

    @Test
    void exchangesTheApiKeyForASingleBearerTokenThatEveryRequestReuses() throws Exception {
        IbmCosWagon wagon = wagon();
        set(wagon, "iamEndpoint", this.endpoint + "/identity/token");
        set(wagon, "serviceInstanceId", "crn:v1:bluemix:public:cloud-object-storage:global:a/account:instance::");
        wagon.connect(repository(), authentication("apikey", "my-api-key"));

        assertTrue(wagon.resourceExists(RESOURCE));
        wagon.put(source(), RESOURCE);
        wagon.get(RESOURCE, destination());
        wagon.disconnect();

        assertTrue(this.tokenRequestBody.contains("grant_type=urn%3Aibm%3Aparams%3Aoauth%3Agrant-type%3Aapikey"),
                this.tokenRequestBody);
        assertTrue(this.tokenRequestBody.contains("apikey=my-api-key"), this.tokenRequestBody);
        assertEquals(1, this.tokenRequests.get(), "the token of the token manager has to be reused");
        assertEquals(3, this.authorizations.size());
        this.authorizations.forEach(authorization -> assertEquals("Bearer fake-token", authorization));
    }

    @Test
    void followsTheMarkerOfATruncatedListing() throws Exception {
        IbmCosWagon wagon = wagon();
        wagon.connect(repository(), authentication("0123456789ABCDEFGHIJ", "secret-access-key"));

        assertEquals(List.of("artifact-1.0.jar", "nested/", "artifact-2.0.jar"), wagon.getFileList("net/integrio/"));
        wagon.disconnect();

        assertEquals(2, this.requests.size(), this.requests.toString());
        assertTrue(this.requests.get(1).contains("marker=release%2Fnet%2Fintegrio%2Fartifact-1.0.jar"),
                this.requests.get(1));
    }

    @Test
    void refusesToConnectWithoutAnEndpoint() {
        assumeTrue(System.getenv(CosUtils.ENDPOINT_VARIABLE) == null && System.getenv(CosUtils.REGION_VARIABLE) == null,
                "the endpoint of the environment would be picked up");

        ConnectionException e = assertThrows(ConnectionException.class, () -> new IbmCosWagon()
                .connect(repository(), authentication("0123456789ABCDEFGHIJ", "secret-access-key")));

        assertTrue(e.getMessage().contains(CosUtils.ENDPOINT_PROPERTY), e.getMessage());
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().toString();
        byte[] body = read(exchange.getRequestBody());

        if (path.startsWith("/identity/token")) {
            this.tokenRequestBody = new String(body, StandardCharsets.UTF_8);
            this.tokenRequests.incrementAndGet();
            long expiration = System.currentTimeMillis() / 1000 + 3600;
            respond(exchange, ("{\"access_token\":\"fake-token\",\"refresh_token\":\"fake-refresh\","
                    + "\"token_type\":\"Bearer\",\"expires_in\":3600,\"expiration\":" + expiration + "}")
                    .getBytes(StandardCharsets.UTF_8), "application/json");
            return;
        }

        this.requests.add(method + " " + path);
        this.authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));

        if ("PUT".equals(method)) {
            this.uploaded = exchange.getRequestHeaders().getFirst("x-amz-decoded-content-length") != null
                    ? decodeAwsChunked(body)
                    : body;
            exchange.getResponseHeaders().add("ETag", '"' + md5(this.uploaded) + '"');
            respond(exchange, new byte[0], null);
            return;
        }

        if (path.contains("?")) {
            respond(exchange, listing(!path.contains("marker=")).getBytes(StandardCharsets.UTF_8), "application/xml");
            return;
        }

        byte[] contents = CONTENTS.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("ETag", '"' + md5(contents) + '"');
        exchange.getResponseHeaders().add("Last-Modified",
                DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC)));

        if ("HEAD".equals(method)) {
            exchange.getResponseHeaders().add("Content-Length", String.valueOf(contents.length));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        respond(exchange, contents, "application/java-archive");
    }

    private static String listing(boolean firstPage) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<ListBucketResult xmlns=\"http://s3.amazonaws.com/doc/2006-03-01/\">"
                + "<Name>my-bucket</Name><Prefix>release/net/integrio/</Prefix><Delimiter>/</Delimiter>"
                + "<MaxKeys>1000</MaxKeys><IsTruncated>" + firstPage + "</IsTruncated>"
                + (firstPage ? "<NextMarker>release/net/integrio/artifact-1.0.jar</NextMarker>" : "")
                + "<Contents><Key>release/net/integrio/artifact-" + (firstPage ? "1.0" : "2.0") + ".jar</Key>"
                + "<LastModified>2026-07-31T10:00:00.000Z</LastModified><Size>17</Size></Contents>"
                + (firstPage ? "" : "<CommonPrefixes><Prefix>release/net/integrio/nested/</Prefix></CommonPrefixes>")
                + "</ListBucketResult>";
    }

    /** unwraps the {@code <hex-size>;chunk-signature=...\r\n<data>\r\n} framing of a signed upload */
    private static byte[] decodeAwsChunked(byte[] body) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        int position = 0;

        while (position < body.length) {
            int lineEnd = position;
            while (lineEnd + 1 < body.length && !(body[lineEnd] == '\r' && body[lineEnd + 1] == '\n')) {
                lineEnd++;
            }
            int size = Integer.parseInt(new String(body, position, lineEnd - position, StandardCharsets.UTF_8)
                    .split(";")[0].trim(), 16);
            if (size == 0) {
                break;
            }
            position = lineEnd + 2;
            payload.write(body, position, size);
            position += size + 2;
        }

        return payload.toByteArray();
    }

    private static void respond(HttpExchange exchange, byte[] body, String contentType) throws IOException {
        if (contentType != null) {
            exchange.getResponseHeaders().add("Content-Type", contentType);
        }

        exchange.sendResponseHeaders(200, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
        exchange.close();
    }

    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;

        while ((count = in.read(buffer)) > 0) {
            out.write(buffer, 0, count);
        }

        return out.toByteArray();
    }

    private static String md5(byte[] contents) {
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("MD5").digest(contents)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private IbmCosWagon wagon() throws Exception {
        IbmCosWagon wagon = new IbmCosWagon();
        set(wagon, "endpoint", this.endpoint);

        return wagon;
    }

    /** emulates the wagon configurator of maven, which injects {@code <server><configuration>} */
    private static void set(IbmCosWagon wagon, String name, String value) throws Exception {
        Field field = IbmCosWagon.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(wagon, value);
    }

    private static Repository repository() {
        return new Repository("cos-release", "cos://my-bucket/release");
    }

    private static AuthenticationInfo authentication(String userName, String password) {
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setUserName(userName);
        authenticationInfo.setPassword(password);

        return authenticationInfo;
    }

    private File source() throws IOException {
        Path source = this.temporaryDirectory.resolve("artifact-1.0.jar");
        Files.write(source, CONTENTS.getBytes(StandardCharsets.UTF_8));

        return source.toFile();
    }

    private File destination() {
        return this.temporaryDirectory.resolve("downloaded.jar").toFile();
    }
}
