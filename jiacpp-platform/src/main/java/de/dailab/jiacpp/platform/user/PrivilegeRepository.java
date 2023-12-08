package de.dailab.jiacpp.platform.user;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PrivilegeRepository extends MongoRepository<Privilege, String> {

    Privilege findByName(String name);

}
