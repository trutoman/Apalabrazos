package Apalabrazos.backend.config;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CosmosDBConfig {
    private static final Logger log = LoggerFactory.getLogger(CosmosDBConfig.class);

    private static final String DATABASE_NAME = "apalabrazosDB";
    private static final String DATABASE_NAME_ENV = "COSMOS_DB_DATABASE";
    private static final String CONTAINER_NAME = "Users";

    private static CosmosClient client;
    private static CosmosDatabase database;
    private static final Map<String, CosmosContainer> containers = new ConcurrentHashMap<>();

    private CosmosDBConfig() {
        // Private constructor to prevent instantiation
    }

    public static synchronized CosmosContainer getUserContainer() {
        return getContainer(CONTAINER_NAME);
    }

    public static synchronized CosmosContainer getContainer(String containerName) {
        CosmosContainer cached = containers.get(containerName);
        if (cached != null) {
            return cached;
        }

        if (client == null || database == null) {
            try {
                String endpoint = System.getenv("COSMOS_DB_ENDPOINT");
                String key = System.getenv("COSMOS_DB_KEY");

                if (endpoint == null || key == null) {
                    log.error("COSMOS_DB_ENDPOINT or COSMOS_DB_KEY environment variables are not set!");
                    log.error(
                            "Expected format for COSMOS_DB_ENDPOINT: https://your-account-name-region.documents.azure.com");
                    throw new RuntimeException("Missing Cosmos DB credentials");
                }

                // Ensure endpoint doesn't end with :443/ (handled by SDK)
                if (endpoint.endsWith(":443/")) {
                    endpoint = endpoint.substring(0, endpoint.length() - 5);
                } else if (endpoint.endsWith("/")) {
                    endpoint = endpoint.substring(0, endpoint.length() - 1);
                }

                log.info("Connecting to Cosmos DB at {}", endpoint);

                client = new CosmosClientBuilder()
                        .endpoint(endpoint)
                        .key(key)
                        .buildClient();

                String databaseName = System.getenv(DATABASE_NAME_ENV);
                if (databaseName == null || databaseName.isBlank()) {
                    databaseName = DATABASE_NAME;
                }
                database = client.getDatabase(databaseName);
                log.info("Using Cosmos DB database: {}", databaseName);

            } catch (Exception e) {
                log.error("Failed to initialize Cosmos DB connection: {}", e.getMessage());
                throw e; // Rethrow to stop application startup if DB is critical
            }
        }

        CosmosContainer resolved = database.getContainer(containerName);
        containers.put(containerName, resolved);
        log.info("✓ Connected to Cosmos DB Container: {}", containerName);
        return resolved;
    }

    public static void close() {
        if (client != null) {
            client.close();
        }
    }
}
