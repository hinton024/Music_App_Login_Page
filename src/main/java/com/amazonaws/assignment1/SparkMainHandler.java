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
    private static final String S3_BUCKET = "s4062787-mybucket"; //  put your S3 bucket name
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
                JSONObject subscription = new JSONObject();
                subscription.put("id", item.getString("id"));
                subscription.put("title", item.getString("title"));
                subscription.put("artist", item.getString("artist"));

                // Add year and album if they exist
                if (item.hasAttribute("year"))
                    subscription.put("year", item.getString("year"));
                if (item.hasAttribute("album"))
                    subscription.put("album", item.getString("album"));
                if (item.hasAttribute("musicId"))
                    subscription.put("musicId", item.getString("musicId"));

                // Fixed path to match upload path in Task2AWSS3.java
                String imageKey = "artist-images/" + item.getString("artist").replace(" ", "") + ".jpg";
                String imageUrl = generatePresignedUrl(imageKey);
                subscription.put("imageUrl", imageUrl);

                subscriptionsArray.put(subscription);
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

            // Get all fields from the request - with more flexible handling
            // The client might send either 'id' or 'musicId' field
            String musicId = requestJson.has("id") ? requestJson.getString("id") :
                    (requestJson.has("musicId") ? requestJson.getString("musicId") :
                            UUID.randomUUID().toString());

            String title = requestJson.getString("title");
            String artist = requestJson.getString("artist");
            String year = requestJson.optString("year", "");
            String album = requestJson.optString("album", "");

            // Generate a unique ID for the subscription
            String subscriptionId = UUID.randomUUID().toString();

            // Create subscription item with all music information
            Item subscriptionItem = new Item()
                    .withPrimaryKey("id", subscriptionId)
                    .withString("userEmail", email)
                    .withString("musicId", musicId)
                    .withString("title", title)
                    .withString("artist", artist)
                    .withLong("timestamp", System.currentTimeMillis());

            // Add optional fields if present
            if (!year.isEmpty()) subscriptionItem.withString("year", year);
            if (!album.isEmpty()) subscriptionItem.withString("album", album);

            // Put the item into the Subscription table
            subscriptionTable.putItem(subscriptionItem);

            JSONObject subscriptionData = new JSONObject();
            subscriptionData.put("id", subscriptionId);
            subscriptionData.put("musicId", musicId);
            subscriptionData.put("title", title);
            subscriptionData.put("artist", artist);
            if (!year.isEmpty()) subscriptionData.put("year", year);
            if (!album.isEmpty()) subscriptionData.put("album", album);

            // Fixed path to match upload path in Task2AWSS3.java
            String imageKey = "artist-images/" + artist.replace(" ", "") + ".jpg";
            String imageUrl = generatePresignedUrl(imageKey);
            subscriptionData.put("imageUrl", imageUrl);

            jsonResponse.put("success", true);
            jsonResponse.put("message", "Successfully subscribed to " + title);
            jsonResponse.put("subscription", subscriptionData);

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

            System.out.println("Search parameters - Title: " + title + ", Artist: " + artist +
                    ", Year: " + year + ", Album: " + album);

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
                // Use equality instead of contains for year, and handle both string and number formats
                filterExpressionBuilder.append("(#yr = :yearString OR #yr = :yearNumber)");
                expressionAttributeValues.put(":yearString", year);
                expressionAttributeValues.put(":yearNumber", Integer.parseInt(year));
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
            String filterExpression = filterExpressionBuilder.toString();

            System.out.println("Filter expression: " + filterExpression);
            System.out.println("Expression attribute values: " + expressionAttributeValues);

            if (filterExpression.length() > 0) {
                items = musicTable.scan(
                        filterExpression,
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

                // Handle the year field which could be a number or string
                if (item.hasAttribute("year")) {
                    Object yearValue = item.get("year");
                    if (yearValue instanceof Number) {
                        musicJson.put("year", String.valueOf(yearValue));
                    } else {
                        musicJson.put("year", item.getString("year"));
                    }
                } else {
                    musicJson.put("year", "");
                }

                musicJson.put("album", item.getString("album"));

                // Fixed path to match upload path in Task2AWSS3.java
                String imageKey = "artist-images/" + item.getString("artist").replace(" ", "") + ".jpg";
                String imageUrl = generatePresignedUrl(imageKey);
                musicJson.put("imageUrl", imageUrl);

                resultsArray.put(musicJson);
            }

            System.out.println("Query returned " + resultsArray.length() + " results");

            if (resultsArray.length() == 0) {
                jsonResponse.put("success", false);
                jsonResponse.put("message", "No result is retrieved. Please query again");
            } else {
                jsonResponse.put("success", true);
                jsonResponse.put("results", resultsArray);
            }

        } catch (Exception e) {
            System.err.println("Error in searchMusic: " + e.getMessage());
            e.printStackTrace();
            jsonResponse.put("success", false);
            jsonResponse.put("message", "Error: " + e.getMessage());
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
