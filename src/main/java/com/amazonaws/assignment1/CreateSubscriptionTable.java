package com.amazonaws.assignment1;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;

public class CreateSubscriptionTable {
    public static void main(String[] args) {
        try {
            System.out.println("Creating Subscription table...");

            AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                    .withRegion(Regions.US_EAST_1)
                    .withCredentials(new DefaultAWSCredentialsProviderChain())
                    .build();

            // Updated schema to include 'id' as the primary key
            // This matches the schema expected by both the Java app and Lambda
            CreateTableRequest request = new CreateTableRequest()
                    .withTableName("Subscription")
                    .withKeySchema(new KeySchemaElement("id", KeyType.HASH))
                    .withAttributeDefinitions(
                        new AttributeDefinition("id", ScalarAttributeType.S))
                    .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L));

            // Optional: Add a Global Secondary Index for userEmail to speed up queries
            GlobalSecondaryIndex emailIndex = new GlobalSecondaryIndex()
                    .withIndexName("userEmail-index")
                    .withKeySchema(new KeySchemaElement("userEmail", KeyType.HASH))
                    .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L))
                    .withProjection(new Projection().withProjectionType(ProjectionType.ALL));
            
            request.withGlobalSecondaryIndexes(emailIndex);
            request.withAttributeDefinitions(
                    new AttributeDefinition("id", ScalarAttributeType.S),
                    new AttributeDefinition("userEmail", ScalarAttributeType.S));

            client.createTable(request);

            System.out.println("Waiting for table to become active...");

            boolean tableActive = false;
            while (!tableActive) {
                Thread.sleep(5000);
                DescribeTableResult description = client.describeTable("Subscription");
                tableActive = description.getTable().getTableStatus().equals("ACTIVE");
                System.out.println("Status: " + description.getTable().getTableStatus());
            }

            System.out.println("Subscription table created successfully!");

        } catch (Exception e) {
            System.err.println("Error creating table: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
