package de.gtarc.opaca.platform.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class JwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    // XXX check what of all this we really need! Baeldung's version is much simples, but class-not-found...

    private final JwtGrantedAuthoritiesConverter converter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                converter.convert(jwt).stream(),
                extractResourceRoles(jwt).stream()
        ).collect(Collectors.toSet());
        System.out.println("AUTHORITIES " + authorities);
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaim(JwtClaimNames.SUB));
    }

    @SuppressWarnings("unchecked")
    private Collection<? extends GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        return ((Collection<String>) realmAccess.get("roles")).stream()
                .filter(role -> role.equals(role.toUpperCase()))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
    }

    /*
    // deutlich einfachere version von Baeldung
    @Bean
    JwtAuthenticationConverter authenticationConverter(AuthoritiesConverter authoritiesConverter) {
      var authenticationConverter = new JwtAuthenticationConverter();
      authenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
        return authoritiesConverter.convert(jwt.getClaims());
      });
      return authenticationConverter;
    }
     */
}
