package de.gtarc.opaca.platform;

import de.flapdoodle.embed.mongo.distribution.IFeatureAwareVersion;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Extends the embedded Mongo autoconfiguration to specify the MongoDB version
 * Is only used when the DB_EMBED is set to 'True'
 */
@Configuration
@ConditionalOnProperty(name = "db_embed", havingValue = "true")
public class EmbeddedMongoConditional extends EmbeddedMongoAutoConfiguration {

    /**
     * Sets the Mongo Version for the embedded MongoDB
     */
    @Bean
    public IFeatureAwareVersion embeddedMongoVersion() {
        return Version.V7_0_4;
    }

}
