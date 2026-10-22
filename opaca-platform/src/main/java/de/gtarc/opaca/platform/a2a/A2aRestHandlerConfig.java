package de.gtarc.opaca.platform.a2a;

import org.a2aproject.sdk.server.AgentCardCacheMetadata;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.auth.TaskAuthorizationProvider;
import org.a2aproject.sdk.server.auth.TaskOperation;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.config.A2AConfigProvider;
import org.a2aproject.sdk.server.events.InMemoryQueueManager;
import org.a2aproject.sdk.server.events.MainEventBus;
import org.a2aproject.sdk.server.events.MainEventBusProcessor;
import org.a2aproject.sdk.server.events.QueueManager;
import org.a2aproject.sdk.server.requesthandlers.DefaultRequestHandler;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.server.tasks.InMemoryTaskStore;
import org.a2aproject.sdk.server.tasks.PushNotificationSender;
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

@Configuration
public class A2aRestHandlerConfig {

    @Bean
    InMemoryTaskStore a2aTaskStore() {
        return new InMemoryTaskStore();
    }

    @Bean
    MainEventBus a2aMainEventBus() {
        return new MainEventBus();
    }

    @Bean
    QueueManager a2aQueueManager(InMemoryTaskStore taskStore, MainEventBus mainEventBus) {
        return new InMemoryQueueManager(taskStore, mainEventBus);
    }

    @Bean(destroyMethod = "close")
    ExecutorService a2aExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    PushNotificationSender a2aPushNotificationSender() {
        return (event, taskSnapshot) -> {};
    }

    @Bean
    A2AConfigProvider a2aConfigProvider(Environment environment) {
        return new A2AConfigProvider() {
            @Override
            public String getValue(String name) {
                return environment.getRequiredProperty(name);
            }

            @Override
            public Optional<String> getOptionalValue(String name) {
                return Optional.ofNullable(environment.getProperty(name));
            }
        };
    }

    @Bean
    MainEventBusProcessor a2aMainEventBusProcessor(
            MainEventBus eventBus,
            InMemoryTaskStore taskStore,
            PushNotificationSender pushSender,
            QueueManager queueManager) {
        return new MainEventBusProcessor(eventBus, taskStore, pushSender, queueManager);
    }

    TaskAuthorizationProvider taskAuthorizationProvider() {
        Map<String, String> owners = new ConcurrentHashMap<>();

        return new TaskAuthorizationProvider() {
            private String authenticatedUsername(ServerCallContext context) {
                User user = context.getUser();
                return "anonymous";
                //return user != null && user.isAuthenticated() ? user.getUsername() : null;
            }

            @Override
            public boolean checkCreate(ServerCallContext context, TaskOperation operation) {
                System.out.println("in check create "  + operation);
                return true;
                //return authenticatedUsername(context) != null;
            }

            @Override
            public boolean checkRead(ServerCallContext context, String taskId, TaskOperation operation) {
                System.out.println("in check read " + operation + " " + taskId);
                String username = authenticatedUsername(context);
                return true;
                //return username != null && (taskId.isEmpty() || username.equals(owners.get(taskId)));
            }

            @Override
            public boolean checkWrite(ServerCallContext context, String taskId, TaskOperation operation) {
                System.out.println("in check write " + operation + " " + taskId);
                String username = authenticatedUsername(context);
                return true;
                //return username != null && username.equals(owners.get(taskId));
            }

            @Override
            public boolean isTaskRecorded(String taskId) {
                System.out.println("in task recorded " + taskId);
                return true;
                //return owners.containsKey(taskId);
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

    RequestHandler a2aRequestHandler(
            AgentExecutor agentExecutor,
            InMemoryTaskStore taskStore,
            QueueManager queueManager,
            MainEventBusProcessor eventProcessor,
            ExecutorService a2aExecutor) {
        return DefaultRequestHandler.builder()
                .agentExecutor(agentExecutor)
                .taskStore(taskStore)
                .queueManager(queueManager)
                .mainEventBusProcessor(eventProcessor)
                .executor(a2aExecutor)
                .eventConsumerExecutor(a2aExecutor)
                .authorizationProvider(taskAuthorizationProvider())
                .pushNotificationsEnabled(false)
                .build();
    }

    @Bean
    RestHandler a2aRestHandler(
            AgentCard agentCard,
            A2AConfigProvider a2aConfigProvider,
            AgentExecutor agentExecutor,
            InMemoryTaskStore taskStore,
            QueueManager queueManager,
            MainEventBusProcessor eventProcessor,
            ExecutorService a2aExecutor) {
        var accmd = new AgentCardCacheMetadata(agentCard, a2aConfigProvider);
        var reqH = a2aRequestHandler(agentExecutor, taskStore, queueManager, eventProcessor, a2aExecutor);
        return new RestHandler(agentCard, accmd, reqH, a2aExecutor);
    }

}
