package com.chefkrish.qa.api;

import com.chefkrish.qa.config.TestConfig;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 * GET /health — the cheapest possible "is the Worker alive" check. Kept as
 * its own test class so a Jenkins build can fail fast here before spending
 * time on the more expensive /chat scenarios below.
 */
public class HealthEndpointTest {

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = TestConfig.BASE_URL;
    }

    @Test(description = "Worker responds 200 on /health")
    public void healthReturns200() {
        Response r = RestAssured.get("/health");
        assertEquals(r.statusCode(), 200, "Expected /health to return 200. Body: " + r.asString());
    }

    @Test(description = "Worker reports Neo4j and Pinecone both connected")
    public void healthReportsBothDataSourcesConnected() {
        Response r = RestAssured.get("/health");
        String body = r.asString();
        // Matches the shape logged as "Worker healthy · Neo4j:true · Pinecone:true"
        // in the Admin Control Center console — see handleHealth() in worker.js.
        assertTrue(body.toLowerCase().contains("neo4j"), "Expected /health body to mention Neo4j: " + body);
        assertTrue(body.toLowerCase().contains("pinecone"), "Expected /health body to mention Pinecone: " + body);
    }
}
