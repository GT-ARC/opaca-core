package de.gtarc.opaca.platform.user;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import de.gtarc.opaca.model.Role;
import de.gtarc.opaca.model.User;
import de.gtarc.opaca.platform.PlatformConfig;
import de.gtarc.opaca.platform.session.SessionData;
import org.bson.Document;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provides basic CRUD functions to interact with a connected Mongo DB.
 * Alternatively can also use a simple in-memory hash-map if no database is available.
 */
@Repository
public class UserRepository {

    private final PlatformConfig config;

    private MongoCollection<Document> collection;
    private Map<String, User> users;

    /**
     * Establishes a connection with either the embedded or external DB
     */
    public UserRepository(PlatformConfig config, SessionData sessionData) {
        this.config = config;
        if (config.dbEmbed) {
            this.users = sessionData.getUsers();
        }
        else {
            MongoClient mongoClient = MongoClients.create(config.dbURI);
            MongoDatabase database = mongoClient.getDatabase(config.dbName);
            collection = database.getCollection("tokenUser");
        }
    }

    public void save(User user) {
        if (config.dbEmbed) {
            users.put(user.getUsername(), user);
        }
        else {
            Document document = new Document();
            document.put("username", user.getUsername());
            document.put("password", user.getPassword());
            document.put("role", user.getRole());
            document.put("privileges", user.getPrivileges());
            collection.insertOne(document);
        }
    }

    public User findByUsername(String username) {
        if (config.dbEmbed) {
            return users.get(username);
        }
        else {
            Document query = new Document("username", username);
            Document result = collection.find(query).first();
            if (result != null) {
                return mapToUser(result);
            }
        }
        return null;
    }

    public List<User> findAll() {
        if (config.dbEmbed) {
            return new ArrayList<>(users.values());
        }
        else {
            List<User> allUsers = new ArrayList<>();
            for (Document document : collection.find()) {
                allUsers.add(mapToUser(document));
            }
            return allUsers;
        }
    }

    public boolean existsByUsername(String username) {
        return findByUsername(username) != null;
    }

    public boolean deleteByUsername(String username) {
        if (config.dbEmbed) {
            return users.remove(username) != null;
        }
        else {
            Document query = new Document("username", username);
            DeleteResult result = collection.deleteOne(query);
            return result.getDeletedCount() > 0;
        }
    }

    // Helper functions

    private User mapToUser(Document document) {
        User user = new User();
        user.setUsername(document.getString("username"));
        user.setPassword(document.getString("password"));
        user.setRole(Role.valueOf(document.getString("role")));
        user.setPrivileges(document.getList("privileges", String.class));
        return user;
    }
}
