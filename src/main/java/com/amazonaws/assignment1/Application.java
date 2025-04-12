package com.amazonaws.assignment1;

import spark.Spark;

public class Application {
    public static void main(String[] args) {
        // Set port (optional, default is 4567)
        Spark.port(8080);

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
        // Add other routes here

        System.out.println("Server started on port " + Spark.port());
    }
}