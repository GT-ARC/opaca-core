package de.gtarc.opaca.platform;

import de.flapdoodle.embed.mongo.distribution.IFeatureAwareVersion;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Overwrites the Mongo autoconfiguration for the usage
 * of the embedded MongoDB
 */
@Configuration
@ConditionalOnProperty(name = "db_type", havingValue = "embedded")
public class EmbeddedMongoConditional extends EmbeddedMongoAutoConfiguration {

    /**
     * Set the Mongo Version for the embedded MongoDB
     */
    @Bean
    public IFeatureAwareVersion embeddedMongoVersion() {
        return Version.V7_0_4;
    }

}
