# Session Handling

The Runtime Platform supports different Session Policies that dictate how it behaves when it is stopped and later restarted. The policy is set with the `SESSION_POLICY` environment variable. Possible values are `SHUTDOWN`, `RESTART`,and `RECONNECT`, described below.


## Policy 'Shutdown'

When the Runtime Platform is stopped, all Agent Containers previously started via the platform are stopped, too, and all connections to other Runtime Platforms are disconnected.

When the Runtime Platform is started again, Agent Containers are started as specified in the `DEFAULT_IMAGE_DIRECTORY` environment variable.


## Policy 'Restart'

When the Runtime Platform is stopped, all Agent Containers previously started via the platform are stopped, too, and all connections to other Runtime Platforms are disconnected.

When the Runtime Platform is started again, the previously stopped Agent Containers are restarted. No additional default-images are started.


## Policy 'Reconnect'

When the Runtime Platform is stopped, all Agent Containers remain running (though they may face problems without their parent platform). All connections to other Runtime Platforms are disconnected.

When the Runtime Platform is started again, the previous session is restored and the platform should automatically reconnect to the running containers and connected platforms.

