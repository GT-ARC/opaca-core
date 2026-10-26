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
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.InternalError;
import org.a2aproject.sdk.transport.rest.handler.RestHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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
            ExecutorService a2aExecutor,
            Environment environment) {

        // Wire up the A2A infrastructure components inline
        var taskStore = new InMemoryTaskStore();
        var eventBus = new MainEventBus();
        var queueManager = new InMemoryQueueManager(taskStore, eventBus);
        var eventProcessor = new MainEventBusProcessor(eventBus, taskStore,
                (event, taskSnapshot) -> {}, queueManager);

        var configProvider = new A2AConfigProvider() {
            @Override
            public String getValue(String name) {
                return environment.getRequiredProperty(name);
            }

            @Override
            public Optional<String> getOptionalValue(String name) {
                return Optional.ofNullable(environment.getProperty(name));
            }
        };

        var authorizationProvider = createAuthorizationProvider();

        var requestHandler = DefaultRequestHandler.builder()
                .agentExecutor(agentExecutor)
                .taskStore(taskStore)
                .queueManager(queueManager)
                .mainEventBusProcessor(eventProcessor)
                .executor(a2aExecutor)
                .eventConsumerExecutor(a2aExecutor)
                .authorizationProvider(authorizationProvider)
                .pushNotificationsEnabled(false)
                .build();

        var agentCardCacheMetadata = new AgentCardCacheMetadata(agentCard, configProvider);
        return new RestHandler(agentCard, agentCardCacheMetadata, requestHandler, a2aExecutor);
    }

    /**
     * This class is still WIP. It's meant to check that only the users who created a task can access it later.
     */
    private TaskAuthorizationProvider createAuthorizationProvider() {
        Map<String, String> owners = new ConcurrentHashMap<>();

        return new TaskAuthorizationProvider() {
            private String authenticatedUsername(ServerCallContext context) {
                return "anonymous";
            }

            @Override
            public boolean checkCreate(ServerCallContext context, TaskOperation operation) {
                System.out.println("in check create " + operation);
                return true;
            }

            @Override
            public boolean checkRead(ServerCallContext context, String taskId, TaskOperation operation) {
                System.out.println("in check read " + operation + " " + taskId);
                return true;
            }

            @Override
            public boolean checkWrite(ServerCallContext context, String taskId, TaskOperation operation) {
                System.out.println("in check write " + operation + " " + taskId);
                return true;
            }

            @Override
            public boolean isTaskRecorded(String taskId) {
                System.out.println("in task recorded " + taskId);
                return true;
            }

            @Override
            public void recordOwnership(ServerCallContext context, String taskId, TaskOperation operation) throws A2AError {
                System.out.println("in record ownership " + operation + " " + taskId);
                String username = authenticatedUsername(context);
                if (username == null) {
                    throw new InternalError("Cannot record task ownership without an authenticated user");
                }
                owners.putIfAbsent(taskId, username);
            }
        };
    }
}
