package UE_Proyecto_Ingenieria.Apalabrazos.backend.repository;

import UE_Proyecto_Ingenieria.Apalabrazos.backend.config.CosmosDBConfig;
import UE_Proyecto_Ingenieria.Apalabrazos.backend.model.User;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosException;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.util.CosmosPagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserRepository {
    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    public void save(User user) {
        try {
            if (user == null) {
                throw new IllegalArgumentException("User must not be null");
            }

            normalizeIdentity(user);
            ensureIds(user);

            CosmosContainer container = CosmosDBConfig.getUserContainer();
            if (container != null) {
                container.createItem(user);
                log.info("User {} saved to Cosmos DB", user.username);
            } else {
                log.warn("Cosmos DB container not initialized, skipping save for user {}", user.username);
                // In production, we might want to throw an exception if DB is critical
            }
        } catch (CosmosException e) {
            log.error("Failed to save user {}: {}", user.username, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error saving user {}: {}", user.username, e.getMessage());
            throw new RuntimeException("Error saving user", e);
        }
    }

    /**
     * Finds a user by email address.
     *
     * @param email The email to search for (will be normalized to lowercase)
     * @return User object if found, null otherwise
     */
    public User findByEmail(String email) {
        try {
            if (email == null || email.isBlank()) {
                return null;
            }

            String normalizedEmail = email.trim().toLowerCase();
            CosmosContainer container = CosmosDBConfig.getUserContainer();

            if (container == null) {
                log.warn("Cosmos DB container not initialized, cannot find user by email");
                return null;
            }

            String query = "SELECT * FROM c WHERE c.email = @email";
            CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

            CosmosPagedIterable<User> results = container.queryItems(
                query.replace("@email", "'" + normalizedEmail + "'"),
                options,
                User.class
            );

            // Get first result
            for (User user : results) {
                log.debug("User found by email: {}", normalizedEmail);
                return user;
            }

            log.debug("No user found with email: {}", normalizedEmail);
            return null;

        } catch (CosmosException e) {
            log.error("Failed to find user by email {}: {}", email, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Unexpected error finding user by email {}: {}", email, e.getMessage());
            return null;
        }
    }

    private void normalizeIdentity(User user) {
        if (user.username != null) {
            user.username = user.username.trim().toLowerCase();
        }
        if (user.email != null) {
            user.email = user.email.trim().toLowerCase();
        }
    }

    private void ensureIds(User user) {
        String generated = java.util.UUID.randomUUID().toString();

        String canonicalId;
        if (user.id != null && !user.id.isBlank()) {
            canonicalId = user.id;
        } else if (user.userId != null && !user.userId.isBlank()) {
            canonicalId = user.userId;
        } else {
            canonicalId = generated;
        }

        user.id = canonicalId;
        user.userId = canonicalId;
    }
}
