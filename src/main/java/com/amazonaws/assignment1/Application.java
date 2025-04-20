package com.amazonaws.assignment1;

import spark.Spark;

public class Application {
    public static void main(String[] args) {
        // Get port from environment variable, command line argument, or use default 80

        int port = getPort(args);
        Spark.ipAddress("0.0.0.0");
        Spark.port(port);

        // Set up static files location
        Spark.staticFiles.location("/static");

        // Add this to your main method in Application.java
        Spark.get("/", (req, res) -> {
            res.redirect("/login.html");
            return null;
        });

        // Set up routes
        SparkLoginHandler.setupLoginRoutes();
        SparkRegisterHandler.setupRegisterRoutes();
        SparkMainHandler.setupMainRoutes();
        // Add other routes here

        System.out.println("Server started on port " + Spark.port());
    }

    private static int getPort(String[] args) {
        // 1. Check environment variable
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                return Integer.parseInt(envPort); // Use the port from environment variable
            } catch (NumberFormatException e) {
                System.err.println("Warning: Invalid PORT environment variable '" + envPort + "'. Checking command line args.");
            }
        }

        // 2. Check command line arguments
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Warning: Invalid port command line argument '" + args[0] + "'. Using default port.");
            }
        }

        // 3. Use default port 8080 if none of the above worked
        int defaultPort = 8080;
        System.out.println("Info: Using default port " + defaultPort);
        return defaultPort;
    }
}

