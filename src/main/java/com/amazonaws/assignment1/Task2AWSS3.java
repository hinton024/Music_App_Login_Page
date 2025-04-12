package com.amazonaws.assignment1;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class Task2AWSS3 {

    private static final String BUCKET_NAME = "s4062787-mybucket"; // Replace with your S3 bucket name

    public static void main(String[] args) {
        // Initialize S3 client
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        // Try different locations for the JSON file
        String[] possiblePaths = {
                "2025a1.json",
                "src/main/resources/2025a1.json",
                System.getProperty("user.dir") + "/2025a1.json"
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
            return;
        }

        try {
            // Read the JSON file
            String jsonContent = new String(Files.readAllBytes(jsonFile.toPath()));

            // Parse the main JSON object and get the songs array
            JSONObject rootObject = new JSONObject(jsonContent);
            JSONArray songsArray = rootObject.getJSONArray("songs");

            // Track unique image URLs to avoid duplicate downloads/uploads
            Set<String> processedImageUrls = new HashSet<>();
            int successCount = 0;

            // Create a temporary directory for downloads if needed
            File tempDir = new File("temp_images");
            if (!tempDir.exists()) {
                tempDir.mkdir();
            }

            // Process each song in the array
            for (int i = 0; i < songsArray.length(); i++) {
                JSONObject songJson = songsArray.getJSONObject(i);
                String imageUrl = songJson.getString("img_url");
                String artist = songJson.getString("artist");

                // Skip if we've already processed this URL
                if (processedImageUrls.contains(imageUrl)) {
                    System.out.println("Skipping duplicate image for artist: " + artist);
                    continue;
                }

                processedImageUrls.add(imageUrl);

                // Extract file name from URL
                String fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1);

                System.out.println("Processing " + fileName + " for artist: " + artist);

                try {
                    // Download the image
                    System.out.println("Downloading from: " + imageUrl);
                    byte[] imageData = downloadImage(imageUrl);

                    // Upload to S3
                    System.out.println("Uploading to S3: " + fileName);
                    uploadToS3(s3Client, fileName, imageData);

                    successCount++;
                    System.out.println("Successfully processed: " + fileName);
                } catch (Exception e) {
                    System.err.println("Error processing image for artist " + artist + ": " + e.getMessage());
                }
            }

            System.out.println("Processing complete. Successfully uploaded " + successCount + " images to S3 bucket: " + BUCKET_NAME);

        } catch (Exception e) {
            System.err.println("Failed to process images:");
            e.printStackTrace();
        }
    }

    private static byte[] downloadImage(String imageUrl) throws Exception {
        URL url = new URL(imageUrl);
        try (InputStream in = url.openStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            return out.toByteArray();
        }
    }

    private static void uploadToS3(AmazonS3 s3Client, String fileName, byte[] imageData) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(imageData.length);
        metadata.setContentType("image/jpeg"); // Assuming all images are JPEGs

        PutObjectRequest request = new PutObjectRequest(
                BUCKET_NAME,
                "artist-images/" + fileName,
                new ByteArrayInputStream(imageData),
                metadata
        );

        s3Client.putObject(request);
    }
}