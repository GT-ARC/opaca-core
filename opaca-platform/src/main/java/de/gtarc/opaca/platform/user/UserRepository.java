package de.gtarc.opaca.platform.user;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.embed.process.io.ProcessOutput;
import de.flapdoodle.reverse.Transition;
import de.flapdoodle.reverse.TransitionWalker;
import de.flapdoodle.reverse.transitions.Start;
import de.gtarc.opaca.model.Role;
import de.gtarc.opaca.model.User;
import de.gtarc.opaca.platform.PlatformConfig;
import org.bson.Document;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides basic CRUD functions to interact with a connected DB
 */
@Repository
public class UserRepository {

    private final MongoCollection<Document> collection;

    /**
     * Establishes a connection with either the embedded or external DB
     */
    public UserRepository(PlatformConfig config) {
        MongoClient mongoClient;
        if(config.dbEmbed) {
            Mongod mongod = new Mongod() {
                // Turn off logging for embedded mongodb
                @Override public Transition<ProcessOutput> processOutput() {
                    return Start.to(ProcessOutput.class)
                            .initializedWith(ProcessOutput.silent())
                            .withTransitionLabel("no output");
                }
            };
            TransitionWalker.ReachedState<RunningMongodProcess> running = mongod.start(Version.V7_0_4);
            ServerAddress serverAddress = running.current().getServerAddress();
            mongoClient = MongoClients.create("mongodb://" + serverAddress);
            MongoDatabase db = mongoClient.getDatabase(config.dbName);
            collection = db.getCollection("tokenUser");
        }
        else {
            mongoClient = MongoClients.create(config.dbURI);
            MongoDatabase database = mongoClient.getDatabase(config.dbName);
            collection = database.getCollection("tokenUser");
        }
    }

    public void save (User user) {
        Document document = new Document();
        document.put("username", user.getUsername());
        document.put("password", user.getPassword());
        document.put("role", user.getRole());
        document.put("privileges", user.getPrivileges());
        collection.insertOne(document);
    }

    public User findByUsername(String username) {
        Document query = new Document("username", username);
        Document result = collection.find(query).first();
        if (result != null) {
            return mapToUser(result);
        }
        return null;
    }

    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        for (Document document : collection.find()) {
            users.add(mapToUser(document));
        }
        return users;
    }

    public boolean existsByName(String username) {
        return findByUsername(username) != null;
    }

    public boolean deleteByUsername(String username) {
        Document query = new Document("username", username);
        DeleteResult result = collection.deleteOne(query);
        return result.getDeletedCount() > 0;
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
