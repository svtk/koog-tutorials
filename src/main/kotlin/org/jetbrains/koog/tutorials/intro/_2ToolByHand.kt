package org.jetbrains.koog.tutorials.intro

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val client = OpenAILLMClient(System.getenv("OPENAI_API_KEY"))
    val model = OpenAIModels.CostOptimized.GPT4_1Mini
    val prompt = prompt(
        id = "tool-by-hand",
        params = LLMParams(temperature = 0.7)
    ) {
        system("""
            You're a banking assistant. You can send money by writing the following JSON:
            { 
                "name": "send_money",
                "params": {
                    "recipient": <recipient_name>,
                    "amount": <amount_in_euros>,
                    "purpose": <purpose_of_the_transaction>
                }
            }
        """.trimIndent())
        user("Send 20 euros to Daniel for dinner at the restaurant")    }

    val responses = client.execute(prompt, model)
    println(responses.first().content)
}