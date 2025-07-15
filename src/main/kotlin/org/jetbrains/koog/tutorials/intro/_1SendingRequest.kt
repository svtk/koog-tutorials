package org.jetbrains.koog.tutorials.intro

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = OpenAILLMClient(System.getenv("OPENAI_API_KEY"))
    val model = OpenAIModels.CostOptimized.GPT4_1Mini

    val userMessage = readln()
    val prompt = prompt(
        id = "translation-request",
        params = LLMParams(temperature = 0.7)
    ) {
        user("Translate into German: $userMessage")
    }
    val responses = client.execute(prompt, model)
    with(responses.single()) {
        println(content)
        println(metaInfo)
    }
}
// Input: You can develop AI Agents in Kotlin!