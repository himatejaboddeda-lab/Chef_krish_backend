package com.chefkrish.qa.api;

import com.chefkrish.qa.config.TestConfig;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

public class OrderEndpointTest {

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = TestConfig.BASE_URL;
    }

    @Test(description = "POST /order with valid items returns a confirmed orderId")
    public void placingOrderReturnsConfirmation() {
        Response r = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of(
                "items", List.of(Map.of("name", "Chicken Saag", "price", 14.99, "quantity", 2)),
                "sessionId", TestConfig.newSessionId("order")
            ))
            .when()
            .post("/order");

        assertEquals(r.statusCode(), 200);
        assertEquals(r.jsonPath().getString("status"), "confirmed");
        assertNotNull(r.jsonPath().getString("orderId"), "Expected an orderId in the response");
    }

    @Test(description = "POST /order with no items is rejected with 400")
    public void placingOrderWithoutItemsIsRejected() {
        Response r = RestAssured.given()
            .contentType(ContentType.JSON)
            .body(Map.of("items", List.of()))
            .when()
            .post("/order");

        assertEquals(r.statusCode(), 400, "Empty item list should be rejected, not silently confirmed.");
    }
}
