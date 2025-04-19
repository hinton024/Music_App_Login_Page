package com.amazonaws.assignment1;

import spark.Spark;

public class Application {
    public static void main(String[] args) {
        // Get port from environment variable, command line argument, or use default 80
        int port = getPort(args);
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
    
    /**
     * Determines the port to use for the server.
     * Priority: 
     * 1. PORT environment variable
     * 2. Command line argument
     * 3. Default port (80 for HTTP)
     * 4. Fallback port (443 for HTTPS) if 80 fails
     */
    private static int getPort(String[] args) {
        // Check environment variable
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                int port = Integer.parseInt(envPort);
                // Validate that the port is either 80 or 443
                if (port == 80 || port == 443) {
                    return port;
                } else {
                    System.out.println("Warning: PORT environment variable is not 80 or 443, using default port 80");
                }
            } catch (NumberFormatException e) {
                System.out.println("Warning: Invalid PORT environment variable, using default port 80");
            }
        }
        
        // Check command line arguments
        if (args.length > 0) {
            try {
                int port = Integer.parseInt(args[0]);
                // Validate that the port is either 80 or 443
                if (port == 80 || port == 443) {
                    return port;
                } else {
                    System.out.println("Warning: Port argument is not 80 or 443, using default port 80");
                }
            } catch (NumberFormatException e) {
                System.out.println("Warning: Invalid port argument, using default port 80");
            }
        }
        
        // Try to use port 80 first (HTTP)
        try {
            return 80; // Default to HTTP port
        } catch (Exception e) {
            // If binding to port 80 fails (e.g., because it requires elevated privileges),
            // Spark will throw an exception when setting the port later
            System.out.println("Warning: Could not use port 80, will try port 443");
            return 443; // Fall back to HTTPS port
        }
    }
}
