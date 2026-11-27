package webserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpTrace;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ProtocolVersion;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Test the Web Server
 * @author Alfonso Fernandez-Barandiaran
 */
class WebServerIT {

    private static final String LOCAL_HOST_TEXT = "http://localhost:";
    private static final String USER_AGENT_TEXT = "User-Agent";
    private static final String USER_AGENT = "Mozilla/5.0";
    private static final Logger logger = LogManager.getLogger(WebServerIT.class);

    private static int serverPort;
    private static String testUrl;
    private static String testBadRequestUrl;
    private static String testNotFoundUrl;

    @BeforeAll
    static void setUpBeforeClass() {
        ServerSettings serverSettings = new ServerSettings(true);
        serverPort = serverSettings.getServerPort();
        logger.info("\nServerPort : {}", serverPort);

        String baseUrl = LOCAL_HOST_TEXT + serverPort;
        testUrl = baseUrl + "/index.html";
        logger.info(testUrl);

        testBadRequestUrl = baseUrl + "/nothing/index.html";
        logger.info(testBadRequestUrl);

        testNotFoundUrl = baseUrl + "/dir/nothing.html";
        logger.info(testNotFoundUrl);
    }

    @Test
    void testGETRequest() {
        executeAndAssertStatus(new HttpGet(testUrl), HttpStatus.SC_OK, "testGETRequest");
    }

    @Test
    void testHEADRequest() {
        executeAndAssertStatus(new HttpHead(testUrl), HttpStatus.SC_OK, "testHEADRequest");
    }

    @Test
    void testPOSTRequest() {
        executeAndAssertStatus(new HttpPost(testUrl), HttpStatus.SC_NOT_IMPLEMENTED, "testPOSTRequest");
    }

    @Test
    void testOPTIONSRequest() {
        executeAndAssertStatus(new HttpOptions(testUrl), HttpStatus.SC_NOT_IMPLEMENTED, "testOPTIONSRequest");
    }

    @Test
    void testPUTRequest() {
        executeAndAssertStatus(new HttpPut(testUrl), HttpStatus.SC_NOT_IMPLEMENTED, "testPUTRequest");
    }

    @Test
    void testDELETERequest() {
        executeAndAssertStatus(new HttpDelete(testUrl), HttpStatus.SC_NOT_IMPLEMENTED, "testDELETERequest");
    }

    @Test
    void testTRACERequest() {
        executeAndAssertStatus(new HttpTrace(testUrl), HttpStatus.SC_NOT_IMPLEMENTED, "testTRACERequest");
    }

    @Test
    void testOKURL() {
        HttpGet request = new HttpGet(testUrl);
        request.addHeader(USER_AGENT_TEXT, USER_AGENT);
        logger.info("\nSending 'GET' request to URL : {}", testUrl);

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            int statusCode = httpclient.execute(request, response -> {
                int code = response.getCode();
                logger.info("Response Code : {}", code);

                if (response.getEntity() != null) {
                    try {
                        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                        logger.info(body);
                        // En JUnit 5 el mensaje va al final
                        assertFalse(body.isEmpty(), "Response body should not be empty");
                    } catch (ParseException e) {
                        throw new IOException("Error parsing response body", e);
                    }
                }
                return code;
            });

            assertEquals(HttpStatus.SC_OK, statusCode);
        } catch (IOException e) {
            logger.error("testOKURL: ", e);
            fail("Request failed: " + e.getMessage());
        }
    }

    @Test
    void testBadRequestURL() {
        executeAndAssertStatus(new HttpGet(testBadRequestUrl), HttpStatus.SC_BAD_REQUEST, "testBadRequestURL");
    }

    @Test
    void testNotFoundURL() {
        executeAndAssertStatus(new HttpGet(testNotFoundUrl), HttpStatus.SC_NOT_FOUND, "testNotFoundURL");
    }

    @Test
    void testImplementedHttp11Version() {
        executeGetWithProtocolVersion(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "testImplementedHttp11Version");
    }

    @Test
    void testImplemented10Version() {
        executeGetWithProtocolVersion(HttpVersion.HTTP_1_0, HttpStatus.SC_OK, "testImplemented10Version");
    }

    @Test
    void testNotImplementedHttp09Version() {
        logger.info("\nSending raw 'GET /index.html HTTP/0.9' request to localhost:{}", serverPort);

        try (Socket socket = new Socket("localhost", serverPort);
             OutputStream out = socket.getOutputStream();
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

            String rawRequest = "GET /index.html HTTP/0.9\r\nHost: localhost\r\n\r\n";
            out.write(rawRequest.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String statusLine = in.readLine();
            logger.info("Raw response status line: {}", statusLine);

            String[] parts = statusLine.split(" ");
            int statusCode = Integer.parseInt(parts[1]);

            assertEquals(HttpStatus.SC_NOT_IMPLEMENTED, statusCode);

        } catch (IOException e) {
            logger.error("testNotImplementedHttp09Version: ", e);
            fail("Request failed: " + e.getMessage());
        }
    }

    private void executeAndAssertStatus(HttpUriRequestBase request, int expectedStatus, String testName) {
        request.addHeader(USER_AGENT_TEXT, USER_AGENT);
        logger.info("\nSending '{}' request to URL : {}", request.getMethod(), request.getRequestUri());

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            int statusCode = httpclient.execute(request, response -> {
                int code = response.getCode();
                logger.info("Response Code : {}", code);
                return code;
            });
            assertEquals(expectedStatus, statusCode);
        } catch (IOException e) {
            logger.error("{}: ", testName, e);
            fail("Request failed: " + e.getMessage());
        }
    }

    private void executeGetWithProtocolVersion(ProtocolVersion version, int expectedStatus, String testName) {
        HttpGet httpGet = new HttpGet(testUrl);
        httpGet.setVersion(version);
        httpGet.addHeader(USER_AGENT_TEXT, USER_AGENT);
        logger.info("\nSending 'GET' {} request to URL : {}", version, testUrl);

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            int statusCode = httpclient.execute(httpGet, response -> {
                logger.info("{} {}", response.getCode(), response.getReasonPhrase());
                return response.getCode();
            });
            assertEquals(expectedStatus, statusCode);
        } catch (IOException e) {
            logger.error("{}: ", testName, e);
            fail("Request failed: " + e.getMessage());
        }
    }
}
