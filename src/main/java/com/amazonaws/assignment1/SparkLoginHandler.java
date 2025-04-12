package com.amazonaws.assignment1;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import org.json.JSONObject;
import spark.Spark;

public class SparkLoginHandler {

    public static void setupLoginRoutes() {
        // Initialize DynamoDB client
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table loginTable = dynamoDB.getTable("Login");

        // Set up the login route
        Spark.post("/api/login", (request, response) -> {
            response.type("application/json");

            try {
                // Parse JSON request
                JSONObject jsonRequest = new JSONObject(request.body());
                String email = jsonRequest.getString("email");
                String password = jsonRequest.getString("password");

                // Look up user by email in DynamoDB
                GetItemSpec spec = new GetItemSpec().withPrimaryKey("email", email);
                Item item = loginTable.getItem(spec);

                JSONObject jsonResponse = new JSONObject();

                if (item != null && item.getString("password").equals(password)) {
                    // Valid credentials
                    request.session(true).attribute("user", email);

                    jsonResponse.put("success", true);
                    jsonResponse.put("message", "Login successful");
                } else {
                    // Invalid credentials
                    jsonResponse.put("success", false);
                    jsonResponse.put("message", "Email or password is invalid");
                }

                return jsonResponse.toString();

            } catch (Exception e) {
                // Handle error
                JSONObject jsonError = new JSONObject();
                jsonError.put("success", false);
                jsonError.put("message", "Login error: " + e.getMessage());

                e.printStackTrace();
                return jsonError.toString();
            }
        });
    }
}