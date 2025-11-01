package org.jetbrains.koog.tutorials.intro4

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable

@Serializable
data class Contact(val id: Int, val name: String, val lastName: String? = null, val phoneNumber: String? = null)

val contactList = listOf(
    Contact(100, "Alice", "Smith", "+1 415 555 1234"),
    Contact(101, "Bob", "Johnson", "+49 151 23456789"),
    Contact(102, "Charlie", "Williams", "+36 20 123 4567"),
    Contact(103, "Daniel", "Anderson", "+46 70 123 45 67"),
    Contact(104, "Daniel", "Garcia", "+34 612 345 678"),
)
val contactMap = contactList.associateBy { it.id }

@LLMDescription("Tools for money transfer operations")
class MoneyTransferTools : ToolSet {
    @Tool
    @LLMDescription(
        "Transfers a specified amount in euros to a recipient contact by their id. "
    )
    fun sendMoney(
        @LLMDescription("The unique identifier of the user initiating the transfer.")
        senderId: Int,

        @LLMDescription("The amount in euros to be transferred.")
        amount: Int,

        @LLMDescription("The unique identifier of the recipient contact.")
        recipientId: Int,

        @LLMDescription("A brief description or reason for the transaction.")
        purpose: String
    ): String {
        val recipient = contactMap[recipientId] ?: return "Invalid recipient."
        println("=======")
        println("Sending $amount EUR to ${recipient.name} ${recipient.lastName} (${recipient.phoneNumber}) with the purpose: \"$purpose\".")
        println("Please confirm the transaction by entering 'yes' or 'no'.")
        println("=======")
        val confirmation = readln()
        return if (confirmation.lowercase() in setOf(
                "yes",
                "y"
            )
        ) "Money was sent." else "Money transfer wasn't confirmed by the user"
    }

    @Tool
    @LLMDescription("Retrieves the list of contacts associated with the user identified by their ID.")
    fun getContacts(
        @LLMDescription("The unique identifier of the user whose contact list is being retrieved.")
        userId: Int
    ): String {
        return contactList.joinToString(separator = "\n") {
            "${it.id}: ${it.name} ${it.lastName ?: ""} (${it.phoneNumber})"
        }
    }

    @Tool
    @LLMDescription("Asks the user to pick the correct recipient of the money transfer when a contact name is confusing or can't be found.")
    fun chooseRecipient(
        @LLMDescription("The unique identifier of the user who initiated the transfer.")
        userId: Int,
        @LLMDescription("The name of the contact that couldn't be found or is ambiguous.")
        confusingRecipientName: String
    ): String {
        println("=======")

        // Find contacts that might match the confusing name
        val possibleMatches = contactList.filter {
            it.name.contains(confusingRecipientName, ignoreCase = true) ||
                    (it.lastName?.contains(confusingRecipientName, ignoreCase = true) ?: false)
        }
        if (possibleMatches.isEmpty()) {
            println("No contact named $confusingRecipientName was found. Here are all available contacts—please choose one:")
        } else {
            println("I found several contacts named $confusingRecipientName. Please choose a recipient from the list below:")
        }

        val contactsToChooseFrom = possibleMatches.ifEmpty { contactList }
        contactsToChooseFrom.forEachIndexed { index, contact ->
            println("${index + 1}. ${contact.name} ${contact.lastName ?: ""} (${contact.phoneNumber})")
        }

        println("Enter the index of the contact you want to choose:")
        println("=======")

        val contactIndex = readln().toIntOrNull()
            ?: throw IllegalArgumentException("Invalid input.")

        val selectedContact = contactsToChooseFrom.getOrNull(contactIndex - 1)
            ?: throw IllegalArgumentException("Invalid input.")

        println("You selected ${selectedContact.name} ${selectedContact.lastName ?: ""} (${selectedContact.phoneNumber}).")
        return "Selected contact: ${selectedContact.id}: ${selectedContact.name} ${selectedContact.lastName ?: ""} (${selectedContact.phoneNumber})."
    }
}

/**
 * Example of how to use the MoneyTransferTools with a chat agent.
 */
fun main() = runBlocking {
    val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))
    val model = OpenAIModels.CostOptimized.GPT4_1Mini

    val toolRegistry = ToolRegistry {
        tools(MoneyTransferTools().asTools())
    }

    val systemPrompt = "You're a banking assistant interacting with a user (userId=123). " +
            "Your goal is to understand the user's request and determine whether it can be fulfilled using the available tools."
    val agent = AIAgent(
        promptExecutor = executor,
        llmModel = model,
        systemPrompt = systemPrompt,
        toolRegistry = toolRegistry
    ) {
        handleEvents {
            onLLMCallStarting { ctx ->
                println("Request to LLM:")
                println("    # Messages:")
                ctx.prompt.messages.forEach { println("    $it") }
                println("    # Tools:")
                ctx.tools.forEach { println("    $it") }
            }
            onLLMCallCompleted { ctx ->
                println("LLM response:")
                ctx.responses.forEach { println("    $it") }
            }
        }
    }

    val message =
        "Send 25 euros to Daniel for dinner at the restaurant."
//        "Transfer 50 euros to Alice for the concert tickets"
    val result = agent.run(message)
    println("Final result:")
    println(result)
}
