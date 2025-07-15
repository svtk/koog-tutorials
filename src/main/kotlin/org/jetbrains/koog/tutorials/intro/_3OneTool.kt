package org.jetbrains.koog.tutorials.intro

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.tool
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor

@Tool
@LLMDescription("Transfers a specified amount in euros to a recipient.")
fun sendMoney(
    @LLMDescription("The amount in euros to be transferred.")
    amount: Int,
    @LLMDescription("The name of the recipient who will receive the money.")
    recipient: String,
    @LLMDescription("A brief description or reason for the transaction.")
    purpose: String
): String {
    println("=======")
    println("Sending $amount EUR to $recipient with the purpose: \"$purpose\".")
    println("Please confirm the transaction by entering 'yes' or 'no'.")
    println("=======")
    // Sending money
    val confirmation = readln()
    return if (confirmation.lowercase() in setOf("yes", "y"))
        "Money was sent."
    else
        "Money transfer wasn't confirmed by the user."
}

suspend fun main() {
    val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))
    val model = OpenAIModels.CostOptimized.GPT4_1Mini

    val toolRegistry = ToolRegistry {
        tool(::sendMoney)
    }
    val agent = AIAgent(
        executor = executor,
        llmModel = model,
        systemPrompt = "You're a banking assistant. Accompany the user with their request.",
        toolRegistry = toolRegistry
    ) {
        handleEvents {
            onBeforeLLMCall { ctx ->
                println("Request to LLM:")
                println("    # Messages:")
                ctx.prompt.messages.forEach { println("    $it") }
                println("    # Tools:")
                ctx.tools.forEach { println("    $it") }
            }
            onAfterLLMCall { ctx ->
                println("LLM response:")
                ctx.responses.forEach { println("    $it") }
            }
        }
    }

    val message = "Send 25 euros to Daniel for dinner at the restaurant."
    val result = agent.run(message)
    println("Final result:")
    println(result)
}
