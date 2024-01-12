package de.gtarc.opaca.platform.user;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TokenUserRepository extends JpaRepository<TokenUser, Long> {

    TokenUser findByUsername(String username);

    Long deleteByUsername(String username);

}
