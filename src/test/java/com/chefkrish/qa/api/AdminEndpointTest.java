package com.chefkrish.qa.api;

import com.chefkrish.qa.config.TestConfig;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * Admin routes require ADMIN_KEY, which is a Cloudflare Worker secret and
 * is deliberately NOT committed anywhere in this repo. Jenkins injects it
 * via withCredentials() (see Jenkinsfile) into -Dchefkrish.adminKey. If
 * it isn't present — e.g. a PR build from a fork, or a local `mvn test`
 * run without the credential — these tests SKIP rather than fail, so
 * missing a secret never blocks the build.
 */
public class AdminEndpointTest {

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = TestConfig.BASE_URL;
        if (TestConfig.ADMIN_KEY == null || TestConfig.ADMIN_KEY.isBlank()) {
            throw new SkipException("chefkrish.adminKey not provided — skipping admin-route tests.");
        }
    }

    @Test(description = "GET /admin-agent-health with a valid key returns 200")
    public void adminHealthWithValidKey() {
        Response r = RestAssured.get("/admin-agent-health?key=" + TestConfig.ADMIN_KEY);
        assertEquals(r.statusCode(), 200);
        assertEquals(r.jsonPath().getString("status"), "ok");
    }

    @Test(description = "Admin route WITHOUT a key is rejected with 401, never silently allowed")
    public void adminRouteWithoutKeyIsRejected() {
        Response r = RestAssured.get("/admin-agent-health");
        assertEquals(r.statusCode(), 401, "Admin route must reject requests with no key.");
    }

    @Test(description = "Admin route with a WRONG key is rejected with 401")
    public void adminRouteWithWrongKeyIsRejected() {
        Response r = RestAssured.get("/admin-agent-health?key=definitely-not-the-real-key");
        assertEquals(r.statusCode(), 401);
    }
}
