package de.gtarc.opaca.platform.user;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.DeleteResult;
import de.gtarc.opaca.model.Role;
import de.gtarc.opaca.platform.PlatformConfig;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class UserRepository {

    private final MongoClient mongoClient;
    private final MongoCollection<Document> collection;

    public UserRepository(PlatformConfig config) {
        mongoClient = MongoClients.create(config.dbURI);
        MongoDatabase database = mongoClient.getDatabase(config.dbName);
        collection = database.getCollection("tokenUser");
    }

    public void closeConnection() {
        mongoClient.close();
    }

    public void save (TokenUser user) {
        Document document = new Document();
        document.put("username", user.getUsername());
        document.put("password", user.getPassword());
        document.put("role", user.getRole());
        document.put("privileges", user.getPrivileges());
        collection.insertOne(document);
    }

    public TokenUser findByUsername(String username) {
        Document query = new Document("username", username);
        Document result = collection.find(query).first();
        if (result != null) {
            return mapToTokenUser(result);
        }
        return null;
    }

    public List<TokenUser> findAll() {
        List<TokenUser> users = new ArrayList<>();
        for (Document document : collection.find()) {
            users.add(mapToTokenUser(document));
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

    private TokenUser mapToTokenUser(Document document) {
        TokenUser user = new TokenUser();
        user.setUsername(document.getString("username"));
        user.setPassword(document.getString("password"));
        user.setPrivileges(document.getList("privileges", String.class));
        user.setRole(Role.valueOf(document.getString("role")));
        return user;
    }
}
