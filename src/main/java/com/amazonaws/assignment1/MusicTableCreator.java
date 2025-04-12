package com.amazonaws.assignment1;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.*;

import java.util.Arrays;

public class MusicTableCreator {

    public static void main(String[] args) {
        // Initialize DynamoDB client
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        DynamoDB dynamoDB = new DynamoDB(client);

        // Define table name
        String tableName = "Music";

        try {
            // Check if table exists and delete if it does
            try {
                System.out.println("Checking if table already exists...");
                Table existingTable = dynamoDB.getTable(tableName);
                existingTable.describe(); // Will throw exception if table doesn't exist

                System.out.println("Table exists. Deleting the existing table...");
                existingTable.delete();
                existingTable.waitForDelete();
                System.out.println("Table deleted successfully.");
            } catch (ResourceNotFoundException e) {
                System.out.println("Table does not exist yet. Will create new.");
            }

            // Create the Music table
            System.out.println("Creating Music table...");
            Table table = dynamoDB.createTable(tableName,
                    Arrays.asList(
                            new KeySchemaElement("title", KeyType.HASH),   // Partition key
                            new KeySchemaElement("artist", KeyType.RANGE)  // Sort key
                    ),
                    Arrays.asList(
                            new AttributeDefinition("title", ScalarAttributeType.S),
                            new AttributeDefinition("artist", ScalarAttributeType.S)
                    ),
                    new ProvisionedThroughput(5L, 5L));

            // Wait for table to become active
            System.out.println("Waiting for table to become active...");
            table.waitForActive();
            System.out.println("Table status: " + table.getDescription().getTableStatus());
            System.out.println("Music table created with attributes: title, artist, year, album, image_url");
            System.out.println("(Note: Only key attributes are defined in the schema)");

        } catch (Exception e) {
            System.err.println("Failed to create table:");
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }
}