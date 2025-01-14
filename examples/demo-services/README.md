# OPACA Demo Services Container

This package contains a simple demo- and test setup that can be used for demoing Reallabor-like services in OPACA. The "themes" of the services is inspired by the Reallabor applications, without them actually using those applications (meaning that it's much more "stable" for demo and testing purposes). The actual services are the same as in the "dummy" module, but they have sensible return values (which are hard-coded in the services for specific inputs). Thus, those services can be used for e.g. testing the LLM or the BPMN interpreter.


## Reallabor Agents

These are the same agents as in https://gitlab.dai-labor.de/zeki-bmas/tp-framework/reallabor-proxy-agents but, in a "dummy-fied" version without calling (or requiring) the actual Reallabor-Applications to work. Please refer to the documentation found there for details on what they do (otherwise documentation will just diverge).


## Additional Dummy Agents

These agents provide additional fictitious services that can be used to demonstrate more complex scenarios, which are currently not supported by the Reallabor Applications
