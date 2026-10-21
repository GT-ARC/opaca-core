# LLM Support

OPACA provides builtin support for Agentic AI, e.g. allowing limited interaction with the OPACA Runtime Platform via an LLM chat assistant.

## OPACA Chat Assistant

The `/query` route can be used to post a natural language query to the OPACA Runtime Platform, where it will be processed by an LLM agent. The model to be used and other details can be configured via environment variables.

The LLM agent has a limited memory, specific to the currently logged in user (and one shared memory for all non-logged-in users), and it has access to all the actions of all agents currently running on this platform. The LLM does not have access to the OPACA platform's full API, i.e. it can not be used to manage containers running on the platform.

The LLM support is still work in progress. We are planning to extend it in various ways:

* improved action handling
* support for more LLM providers (currently, only OpenAI is supported)
* support for scheduling tasks for later, recurring tasks, or long-running background tasks
* support for the Agent-2-Agent protocol for interaction with other agentic systems
