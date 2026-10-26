package de.gtarc.opaca.platform.a2a;

import org.a2aproject.sdk.server.AgentCardCacheMetadata;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Autowires together a RestHandler for A2A.
 * The library is originally intended for Quarkus, and using it with Spring Boot requires some extra steps.
 */
@Configuration
public class A2aRestHandlerConfig {

    @Bean(destroyMethod = "close")
    ExecutorService a2aExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    RestHandler a2aRestHandler(
            AgentCard agentCard,
            AgentExecutor agentExecutor,
            ExecutorService a2aExecutor) {

        // Wire up the A2A infrastructure components inline
        var taskStore = new InMemoryTaskStore();
        var eventBus = new MainEventBus();
        var queueManager = new InMemoryQueueManager(taskStore, eventBus);
        var eventProcessor = new MainEventBusProcessor(eventBus, taskStore,
                (event, taskSnapshot) -> {}, queueManager);

        // No-op config provider: AgentCardCacheMetadata only reads "a2a.agent-card.cache.max-age"
        // and falls back to 3600 if not set, so returning empty is sufficient.
        A2AConfigProvider configProvider = new A2AConfigProvider() {
            @Override public String getValue(String name) { throw new IllegalArgumentException(name); }
            @Override public Optional<String> getOptionalValue(String name) { return Optional.empty(); }
        };

        var requestHandler = DefaultRequestHandler.builder()
                .agentExecutor(agentExecutor)
                .taskStore(taskStore)
                .queueManager(queueManager)
                .mainEventBusProcessor(eventProcessor)
                .executor(a2aExecutor)
                .eventConsumerExecutor(a2aExecutor)
                .authorizationProvider(new TaskAuthorizationProviderImpl())
                .pushNotificationsEnabled(false)
                .build();

        var agentCardCacheMetadata = new AgentCardCacheMetadata(agentCard, configProvider);
        return new RestHandler(agentCard, agentCardCacheMetadata, requestHandler, a2aExecutor);
    }

    /**
     * Simple in-memory task authorization provider that tracks task ownership by user.
     * Only the user who created a task can read or write to it.
     * Based on the sample implementation from the A2A SDK documentation.
     */
    private static class TaskAuthorizationProviderImpl implements TaskAuthorizationProvider {
        private final ConcurrentMap<String, String> ownershipStore = new ConcurrentHashMap<>();

        @Override
        public boolean checkRead(ServerCallContext context, String taskId, TaskOperation operation) {
            return isOwner(context, taskId);
        }

        @Override
        public boolean checkWrite(ServerCallContext context, String taskId, TaskOperation operation) {
            return isOwner(context, taskId);
        }

        @Override
        public boolean checkCreate(ServerCallContext context, TaskOperation operation) {
            // Allow any user (authenticated or not) to create a task
            return true;
        }

        @Override
        public boolean isTaskRecorded(String taskId) {
            return ownershipStore.containsKey(taskId);
        }

        @Override
        public void recordOwnership(ServerCallContext context, String taskId, TaskOperation operation) {
            var user = context.getUser();
            if (user.isAuthenticated()) {
                ownershipStore.putIfAbsent(taskId, user.getUsername());
            }
        }

        private boolean isOwner(ServerCallContext context, String taskId) {
            // allow access if task was created by unauthenticated user or same user
            String owner = ownershipStore.get(taskId);
            return owner == null || owner.equals(context.getUser().getUsername());
        }
    }
}
