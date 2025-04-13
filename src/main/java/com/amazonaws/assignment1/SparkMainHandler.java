package com.amazonaws.assignment1;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;
import spark.Spark;

import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SparkMainHandler {

    private static final String MUSIC_TABLE = "Music";
    private static final String SUBSCRIPTION_TABLE = "Subscription";
    private static final String S3_BUCKET = "s4062787-mybucket"; // Replace with your S3 bucket name
    private static DynamoDB dynamoDB;
    private static AmazonS3 s3Client;
    private static Table musicTable;
    private static Table subscriptionTable;
    private static Table loginTable;

    public static void setupMainRoutes() {
        // Initialize AWS clients
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();
        dynamoDB = new DynamoDB(client);

        s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        musicTable = dynamoDB.getTable(MUSIC_TABLE);
        subscriptionTable = dynamoDB.getTable(SUBSCRIPTION_TABLE);
        loginTable = dynamoDB.getTable("Login");

        // Get current user info
        Spark.get("/api/current-user", SparkMainHandler::getCurrentUser);

        // Subscriptions endpoints
        Spark.get("/api/subscriptions", SparkMainHandler::getUserSubscriptions);
        Spark.post("/api/subscriptions", SparkMainHandler::addSubscription);
        Spark.delete("/api/subscriptions/:id", SparkMainHandler::removeSubscription);

        // Music search endpoint
        Spark.get("/api/music/search", SparkMainHandler::searchMusic);
    }

    private static String getCurrentUser(Request request, Response response) {
        response.type("application/json");
        JSONObject jsonResponse = new JSONObject();

        try {
            // Get user email from session
            String email = request.session().attribute("userEmail");

            if (email == null) {
                jsonResponse.put("success", false);
                jsonResponse.put("message", "No user is logged in");
                return jsonResponse.toString();
            }

            // Fetch user from DynamoDB
            Item user = loginTable.getItem("email", email);

            if (user == null) {
                jsonResponse.put("success", false);
                jsonResponse.put("message", "User not found");
                return jsonResponse.toString();
            }

            JSONObject userJson = new JSONObject();
            userJson.put("email", user.getString("email"));
            userJson.put("username", user.getString("username"));

            jsonResponse.put("success", true);
            jsonResponse.put("user", userJson);

        } catch (Exception e) {
            jsonResponse.put("success", false);
            jsonResponse.put("message", "Error: " + e.getMessage());
            e.printStackTrace();
        }

        return jsonResponse.toString();
    }

    private static String getUserSubscriptions(Request request, Response response) {
        response.type("application/json");
        JSONObject jsonResponse = new JSONObject();

        try {
            // Get user email from session
            String email = request.session().attribute("userEmail");

            if (email == null) {
                jsonResponse.put("success", false);
                jsonResponse.put("message", "No user is logged in");
                return jsonResponse.toString();
            }

            // Query subscriptions table
            Map<String, Object> expressionAttributeValues = new HashMap<String, Object>();
            expressionAttributeValues.put(":email", email);

            ItemCollection<ScanOutcome> items = subscriptionTable.scan(
                    "userEmail = :email",
                    null,
                    expressionAttributeValues);

            // Convert to JSON array
            JSONArray subscriptionsArray = new JSONArray();

            for (Item item : items) {
                String musicId = item.getString("musicId");

                // Get music details
                Item musicItem = musicTable.getItem("id", musicId);

                if (musicItem != null) {
                    JSONObject subscription = new JSONObject();
                    subscription.put("id", item.getString("id")); // This line adds the subscription ID
                    subscription.put("title", musicItem.getString("title"));
                    subscription.put("artist", musicItem.getString("artist"));
                    subscription.put("year", musicItem.getString("year"));
                    subscription.put("album", musicItem.getString("album"));

                    // Fixed path to match upload path in Task2AWSS3.java
                    String imageKey = "artist-images/" + musicItem.getString("artist").replace(" ", "") + ".jpg";
                    String imageUrl = generatePresignedUrl(imageKey);
                    subscription.put("imageUrl", imageUrl);

                    subscriptionsArray.put(subscription);
                }
            }

            jsonResponse.put("success", true);
            jsonResponse.put("subscriptions", subscriptionsArray);

        } catch (Exception e) {
            jsonResponse.put("success", false);
            jsonResponse.put("message", "Error: " + e.getMessage());
            e.printStackTrace();
        }

        return jsonResponse.toString();
    }

    private static String addSubscription(Request request, Response response) {
        response.type("application/json");
        JSONObject jsonResponse = new JSONObject();

        try {
            String body = request.body();
            JSONObject requestJson = new JSONObject(body);

            // Debug the incoming request
            System.out.println("Subscription request body: " + body);

            // Get user email from session
            String email = request.session().attribute("userEmail");
            if (email == null) {
                jsonResponse.put("success", false);
                jsonResponse.put("message", "No user is logged in");
                return jsonResponse.toString();
            }

            // Get the necessary fields from the request
            String title = requestJson.has("title") ? requestJson.getString("title") : "";
            String artist = requestJson.has("artist") ? requestJson.getString("artist") : "";

            // Generate a unique ID for the subscription
            String subscriptionId = UUID.randomUUID().toString();

            // Create subscription item
            Item subscriptionItem = new Item()
                    .withPrimaryKey("id", subscriptionId)
                    .withString("email", email)
                    .withString("title", title)
                    .withString("artist", artist)
                    .withLong("timestamp", System.currentTimeMillis());

            // Put the item into the Subscription table
            subscriptionTable.putItem(subscriptionItem);

            jsonResponse.put("success", true);
            jsonResponse.put("message", "Successfully subscribed to " + title);

        } catch (Exception e) {
            jsonResponse.put("success", false);
            jsonResponse.put("message", "Error: " + e.getMessage());
            e.printStackTrace();
        }

        return jsonResponse.toString();
    }

    private static String removeSubscription(Request request, Response response) {
        response.type("application/json");
        JSONObject jsonResponse = new JSONObject();

        try {
            // Get user email from session
            String email = request.session().attribute("userEmail");
            if (email == null) {
                jsonResponse.put("success", false);
                jsonResponse.put("message", "No user is logged in");
                return jsonResponse.toString();
            }

            // Get subscription ID from path parameter
            String subscriptionId = request.params(":id");
            if (subscriptionId == null) {
                jsonResponse.put("success", false);
                jsonResponse.put("message", "No subscription ID provided");
                return jsonResponse.toString();
            }

            // Delete the subscription directly by ID
            subscriptionTable.deleteItem("id", subscriptionId);
            jsonResponse.put("success", true);
            jsonResponse.put("message", "Subscription removed successfully");
        } catch (Exception e) {
            jsonResponse.put("success", false);
            jsonResponse.put("message", "Error: " + e.getMessage());
            e.printStackTrace();
        }

        return jsonResponse.toString();
    }

    private static String searchMusic(Request request, Response response) {
        response.type("application/json");
        JSONObject jsonResponse = new JSONObject();

        try {
            String title = request.queryParams("title");
            String artist = request.queryParams("artist");
            String year = request.queryParams("year");
            String album = request.queryParams("album");

            // Build filter expression and attribute values
            StringBuilder filterExpressionBuilder = new StringBuilder();
            Map<String, Object> expressionAttributeValues = new HashMap<String, Object>();
            Map<String, String> expressionAttributeNames = new HashMap<String, String>();

            if (title != null && !title.trim().isEmpty()) {
                filterExpressionBuilder.append("contains(title, :title)");
                expressionAttributeValues.put(":title", title);
            }

            if (artist != null && !artist.trim().isEmpty()) {
                if (filterExpressionBuilder.length() > 0) {
                    filterExpressionBuilder.append(" and ");
                }
                filterExpressionBuilder.append("contains(artist, :artist)");
                expressionAttributeValues.put(":artist", artist);
            }

            if (year != null && !year.trim().isEmpty()) {
                if (filterExpressionBuilder.length() > 0) {
                    filterExpressionBuilder.append(" and ");
                }
                filterExpressionBuilder.append("contains(#yr, :year)");
                expressionAttributeValues.put(":year", year);
                expressionAttributeNames.put("#yr", "year");
            }

            if (album != null && !album.trim().isEmpty()) {
                if (filterExpressionBuilder.length() > 0) {
                    filterExpressionBuilder.append(" and ");
                }
                filterExpressionBuilder.append("contains(album, :album)");
                expressionAttributeValues.put(":album", album);
            }

            // Execute scan with filter
            ItemCollection<ScanOutcome> items;
            if (filterExpressionBuilder.length() > 0) {
                items = musicTable.scan(
                        filterExpressionBuilder.toString(),
                        expressionAttributeNames.isEmpty() ? null : expressionAttributeNames,
                        expressionAttributeValues
                );
            } else {
                items = musicTable.scan();
            }

            // Convert results to JSON
            JSONArray resultsArray = new JSONArray();

            for (Item item : items) {
                JSONObject musicJson = new JSONObject();
                musicJson.put("id", item.getString("id"));
                musicJson.put("title", item.getString("title"));
                musicJson.put("artist", item.getString("artist"));
                musicJson.put("year", item.getString("year"));
                musicJson.put("album", item.getString("album"));

                // Fixed path to match upload path in Task2AWSS3.java
                String imageKey = "artist-images/" + item.getString("artist").replace(" ", "") + ".jpg";
                String imageUrl = generatePresignedUrl(imageKey);
                musicJson.put("imageUrl", imageUrl);

                resultsArray.put(musicJson);
            }

            if (resultsArray.length() == 0) {
                jsonResponse.put("success", false);
                jsonResponse.put("message", "No result is retrieved. Please query again");
            } else {
                jsonResponse.put("success", true);
                jsonResponse.put("results", resultsArray);
            }

        } catch (Exception e) {
            jsonResponse.put("success", false);
            jsonResponse.put("message", "Error: " + e.getMessage());
            e.printStackTrace();
        }

        return jsonResponse.toString();
    }

    private static String generatePresignedUrl(String objectKey) {
        // Generate a pre-signed URL that expires in 1 hour
        java.util.Date expiration = new java.util.Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += TimeUnit.HOURS.toMillis(1);
        expiration.setTime(expTimeMillis);

        GeneratePresignedUrlRequest generatePresignedUrlRequest =
                new GeneratePresignedUrlRequest(S3_BUCKET, objectKey)
                        .withMethod(com.amazonaws.HttpMethod.GET)
                        .withExpiration(expiration);

        URL url = s3Client.generatePresignedUrl(generatePresignedUrlRequest);
        return url.toString();
    }
}