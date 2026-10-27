# LLM Support

OPACA provides builtin support for Agentic AI, e.g. allowing limited interaction with the OPACA Runtime Platform via an LLM chat assistant.

## OPACA Chat Assistant

The `/query` route can be used to post a natural language query to the OPACA Runtime Platform, where it will be processed by an LLM agent. The model to be used and other details can be configured via environment variables.

The LLM agent is based on [LangChain4J](https://docs.langchain4j.dev). It has a limited memory, specific to the currently logged in user (and one shared memory for all non-logged-in users), and it has access to all the actions of all agents currently running on this platform. The LLM currently does _not_ have access to actions running on connected platforms. Also, the LLM does not have access to the OPACA platform's full API, i.e. it can not be used to, e.g., manage containers running on the platform.

The LLM support is still work in progress. We are planning to extend it in various ways:

* improved action handling
* support for more LLM providers (currently, only OpenAI is supported)
* support for scheduling tasks for later, recurring tasks, or long-running background tasks


## Configuration and Environment Variables

LLM-Support is configured using a number of environment variables that can be passed when the platform is started, e.g. in a Docker-Compose file.

* `LLM_MODEL_NAME`: The LLM to be used for the chat assistant, e.g. `gpt-4o-mini`; if missing, no chat assistant is available (default: empty)
* `LLM_API_KEY`: The API Key for the LLM, has to match the model, e.g. for OpenAI; if missing, no chat assistant is available (default: empty)
* `LLM_MEMORY_WINDOW`: How many messages are retained in the chat assistants memory; includes both the user's and the assistant's messages (default: 10)
* `LLM_MAX_ACTION_ITERS`: Max number of tool calling iterations the LLM will perform before "giving up" (default: 5)


## Interaction

### A2A Support

The OPACA platform supports the A2A protocol. The Agent-card can be found at `/.well-known/agent-card.json` and messages can be sent to `/a2a/message:send`. Internally, they delegate to the above described `/query` method.

Note that A2A support in OPACA is still work in progress. It allows sending synchronous messages to the agent and receiving a response. More advanced parts of the protocol are not yet fully supported.


### MCP Support

The actions of all agents running on the OPACA platform are provided as MCP tools and can be used by other LLM. This will _not_ invoke the LLM running on this platform (or require it to run in the frist place) but call the corresponding action directly, as if calling the `/invoke` route. Please see the [docs on the MCP integration](mcp.md) for details.
