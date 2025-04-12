package com.amazonaws.assignment1;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.model.*;

import java.util.Collections;

public class LoginTableCreator {

    public static void main(String[] args) {
        // Initialize DynamoDB client using LabRole (via default credentials provider)
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        DynamoDB dynamoDB = new DynamoDB(client);

        // Define table name
        String tableName = "Login";
        Table table;

        try {
            // Check if table exists
            try {
                System.out.println("Checking if table already exists...");
                table = dynamoDB.getTable(tableName);
                table.describe(); // Will throw exception if table doesn't exist
                System.out.println("Table already exists. Using existing table.");
            } catch (ResourceNotFoundException e) {
                // Table doesn't exist, create it
                System.out.println("Creating Login table...");
                table = dynamoDB.createTable(tableName,
                        Collections.singletonList(new KeySchemaElement("email", KeyType.HASH)), // Email is partition key
                        Collections.singletonList(new AttributeDefinition("email", ScalarAttributeType.S)),
                        new ProvisionedThroughput(5L, 5L));

                // Wait for table to become active
                System.out.println("Waiting for table to become active...");
                table.waitForActive();
                System.out.println("Table status: " + table.getDescription().getTableStatus());
            }

            // Step 2: Insert 10 records
            String studentId = "s4062787"; // Replace with your actual student ID
            String name = "PreetpalSingh"; // Replace with your actual name

            // Define passwords as per requirements
            String[] passwords = {"012345", "123456", "234567", "345678", "456789",
                    "567890", "678901", "789012", "890123", "901234"};

            // Create and insert 10 items
            for (int i = 0; i < 10; i++) {
                String email = studentId + i + "@student.rmit.edu.au";
                String userName = name + i;

                System.out.println("Adding item: " + email);

                Item item = new Item()
                        .withPrimaryKey("email", email)
                        .withString("user_name", userName)
                        .withString("password", passwords[i]);

                table.putItem(item);
            }

            System.out.println("Login table populated with 10 items.");

            // Step 3: Display the final table contents
            System.out.println("\nVerifying table contents:");
            System.out.println("------------------------");

            // Scan the table and print all items
            ScanRequest scanRequest = new ScanRequest().withTableName(tableName);
            ScanResult result = client.scan(scanRequest);

            // Print table header
            System.out.printf("%-40s %-20s %-15s\n", "Email", "Username", "Password");
            System.out.println("--------------------------------------------------------------------");

            // Print each item
            for (java.util.Map<String, AttributeValue> item : result.getItems()) {
                System.out.printf("%-40s %-20s %-15s\n",
                        item.get("email").getS(),
                        item.get("user_name").getS(),
                        item.get("password").getS());
            }

            System.out.println("\nTotal items in table: " + result.getCount());

        } catch (Exception e) {
            System.err.println("Failed to work with table:");
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }
}