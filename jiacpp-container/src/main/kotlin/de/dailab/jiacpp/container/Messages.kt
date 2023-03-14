package de.dailab.jiacpp.container

import com.fasterxml.jackson.databind.JsonNode


data class Invoke(val name: String, val parameters: Map<String, JsonNode>)
