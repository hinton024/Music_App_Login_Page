package com.amazonaws.assignment1;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;

import spark.ModelAndView;
import spark.Spark;
import spark.template.velocity.VelocityTemplateEngine;

import java.util.HashMap;
import java.util.Map;

public class Task3Loginpage {

    private static final String LOGIN_TABLE = "login";

    public static void main(String[] args) {
        // Set up Spark web framework
        Spark.port(8080);
        Spark.staticFiles.location("/public");

        // Initialize template engine
        VelocityTemplateEngine engine = new VelocityTemplateEngine();

        // Initialize DynamoDB client
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_EAST_1)
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();

        DynamoDB dynamoDB = new DynamoDB(client);
        Table loginTable = dynamoDB.getTable(LOGIN_TABLE);

        // Configure routes with template engine
        Spark.get("/", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            return new ModelAndView(model, "Loginpage.vm");
        }, engine);

        Spark.get("/login", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            return new ModelAndView(model, "Loginpage.vm");
        }, engine);

        Spark.post("/login", (req, res) -> {
            String email = req.queryParams("email");
            String password = req.queryParams("password");
            Map<String, Object> model = new HashMap<>();

            try {
                // Query DynamoDB to check user credentials
                GetItemSpec spec = new GetItemSpec()
                        .withPrimaryKey("email", email);

                Item item = loginTable.getItem(spec);

                if (item != null && password.equals(item.getString("password"))) {
                    // Successful login
                    req.session(true).attribute("user", email);
                    res.redirect("/dashboard");
                    return null;
                } else {
                    // Failed login
                    model.put("error", "Invalid email or password");
                    return new ModelAndView(model, "Loginpage.vm");
                }
            } catch (Exception e) {
                e.printStackTrace();
                model.put("error", "Error connecting to database");
                return new ModelAndView(model, "Loginpage.vm");
            }
        }, engine);

        Spark.get("/register", (req, res) -> {
            Map<String, Object> model = new HashMap<>();
            return new ModelAndView(model, "Register.vm");
        }, engine);

        Spark.get("/dashboard", (req, res) -> {
            // Check if user is logged in
            String user = req.session().attribute("user");
            if (user == null) {
                res.redirect("/login");
                return null;
            }

            Map<String, Object> model = new HashMap<>();
            model.put("user", user);
            return new ModelAndView(model, "Dashboard.vm");
        }, engine);

        Spark.get("/logout", (req, res) -> {
            req.session().invalidate();
            res.redirect("/login");
            return null;
        });

        System.out.println("Server started on http://localhost:8080");
    }
}