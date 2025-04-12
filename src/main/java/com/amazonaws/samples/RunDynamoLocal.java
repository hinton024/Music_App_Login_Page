package com.amazonaws.samples;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

class RunDynamoDBLocal {
    public static void main(String[] args) {
        try {
            // Define the command
            String[] command = {
                    "java",
                    "-Djava.library.path=C:\\Preetpal\\OneDrive - RMIT University\\RMIT\\Semester 2\\Cloud Computing\\week_4\\dynamodb_local_latest\\DynamoDBLocal_lib",
                    "-jar",
                    "DynamoDBLocal.jar",
                    "-sharedDb"
            };

            // Set the working directory (where the JAR file is located)
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File("path/to/dynamodb-local"));

            // Start the process
            Process process = processBuilder.start();

            // Optionally, print output or errors from the process
            new Thread(() -> {
                try (InputStream inputStream = process.getInputStream()) {
                    inputStream.reset();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            new Thread(() -> {
                try (InputStream errorStream = process.getErrorStream()) {
                    errorStream.reset();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}