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

public class SparkRegisterHandler {

    public static void setupRegisterRoutes() {
        // Initialize DynamoDB client
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();
        DynamoDB dynamoDB = new DynamoDB(client);
        Table loginTable = dynamoDB.getTable("Login");

        // Set up the register route
        Spark.post("/api/register", (request, response) -> {
            response.type("application/json");

            try {
                // Parse JSON request
                JSONObject jsonRequest = new JSONObject(request.body());
                String email = jsonRequest.getString("email");
                String username = jsonRequest.getString("username");
                String password = jsonRequest.getString("password");

                // Check if email already exists
                GetItemSpec spec = new GetItemSpec().withPrimaryKey("email", email);
                Item existingUser = loginTable.getItem(spec);

                JSONObject jsonResponse = new JSONObject();

                if (existingUser != null) {
                    // Email already exists
                    jsonResponse.put("success", false);
                    jsonResponse.put("message", "The email already exists");
                } else {
                    // Create new user
                    Item newUser = new Item()
                            .withPrimaryKey("email", email)
                            .withString("username", username)
                            .withString("password", password);

                    loginTable.putItem(newUser);

                    jsonResponse.put("success", true);
                    jsonResponse.put("message", "Registration successful");
                }

                return jsonResponse.toString();

            } catch (Exception e) {
                // Handle error
                JSONObject jsonError = new JSONObject();
                jsonError.put("success", false);
                jsonError.put("message", "Registration error: " + e.getMessage());

                e.printStackTrace();
                return jsonError.toString();
            }
        });
    }
}