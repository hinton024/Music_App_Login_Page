package com.amazonaws.assignment1;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;

public class MusicDataLoader {

    public static void main(String[] args) {
        // Initialize DynamoDB client
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        DynamoDB dynamoDB = new DynamoDB(client);
        String tableName = "Music";
        Table table = dynamoDB.getTable(tableName);

        // Try different locations for the JSON file
        String[] possiblePaths = {
                "2025a1.json",                           // Project root
                "src/main/resources/2025a1.json",        // Resources directory
                System.getProperty("user.dir") + "/2025a1.json"  // Absolute path to project root
        };

        File jsonFile = null;
        for (String path : possiblePaths) {
            File file = new File(path);
            if (file.exists()) {
                jsonFile = file;
                System.out.println("Found JSON file at: " + file.getAbsolutePath());
                break;
            }
        }

        if (jsonFile == null) {
            System.err.println("Could not find 2025a1.json in any expected location.");
            System.err.println("Please place the file in the project root directory.");
            return;
        }

        try {
            System.out.println("Loading data from " + jsonFile.getName() + " into " + tableName + " table...");

            // Read the JSON file
            String jsonContent = new String(Files.readAllBytes(jsonFile.toPath()));

            // Parse the main JSON object and get the songs array
            JSONObject rootObject = new JSONObject(jsonContent);
            JSONArray songsArray = rootObject.getJSONArray("songs");

            int successCount = 0;

            // Process each song in the songs array
            for (int i = 0; i < songsArray.length(); i++) {
                JSONObject songJson = songsArray.getJSONObject(i);

                // Extract attributes from JSON
                String title = songJson.getString("title");
                String artist = songJson.getString("artist");
                String yearStr = songJson.getString("year");
                int year = Integer.parseInt(yearStr);
                String album = songJson.getString("album");
                String imageUrl = songJson.getString("img_url"); // Note: using img_url from JSON

                // Create an item with all attributes
                Item item = new Item()
                        .withPrimaryKey("title", title, "artist", artist)
                        .withNumber("year", year)
                        .withString("album", album)
                        .withString("image_url", imageUrl); // Still using image_url in DynamoDB

                // Add the item to the table
                table.putItem(item);
                successCount++;

                System.out.printf("Added item: %s by %s (%d)%n", title, artist, year);
            }

            System.out.println("Successfully loaded " + successCount + " items into the Music table.");
            System.out.println("\nData load complete. You can verify the data in the AWS Console.");

        } catch (Exception e) {
            System.err.println("Failed to load data into table:");
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }
}