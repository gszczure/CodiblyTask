package org.codibly.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
class GenerationControllerIntegrationTest {

    @LocalServerPort
    private int port;

    private static final String BASE_PATH = "/api/v1/";

    @BeforeEach
    void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;
    }

    @Test
    @DisplayName("Should return 200 OK with JSON for the last three days of energy generation and the response should not be empty")
    void getThreeDaysAverage_happyPath_returnResponse() {
        given()
                .accept(ContentType.JSON)
                .basePath(BASE_PATH)
                .when()
                .get("generation/three-days")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("", not(empty()));
    }

    @Test
    @DisplayName("Should return 200 OK with JSON for the optimal charging window for valid hours and the response should not be empty")
    void findOptimalChargingWindow_happyPath_returnResponse() {
        given()
                .accept(ContentType.JSON)
                .param("hours", 3)
                .when()
                .get("/api/v1/charge-window")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("", not(empty()));
    }

    @Test
    @DisplayName("Should return 400 BAD_REQUEST when the number of hours is invalid")
    void findOptimalChargingWindow_unhappyPath_InvalidHours_returnReturnBadRequest() {
        given()
                .accept(ContentType.JSON)
                .param("hours", 10)
                .when()
                .get("/api/v1/charge-window")
                .then()
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .contentType(ContentType.JSON)
                .body("status", equalTo(400))
                .body("errorMessage", equalTo("BAD_REQUEST"))
                .body("message", equalTo("Charging window length must be between 1 and 6 hours"))
                .body("path", equalTo("/api/v1/charge-window"));
    }
}
