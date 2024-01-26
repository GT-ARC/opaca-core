package de.gtarc.opaca.platform.user;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TokenUserRepository extends MongoRepository<TokenUser, String> {

    TokenUser findByUsername(String username);

    Long deleteByUsername(String username);

}
