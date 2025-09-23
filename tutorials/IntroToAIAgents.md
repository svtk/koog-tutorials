## Introduction to AI agents in Kotlin

It's so easy to create an AI agent in Kotlin—let's see how! More and more software is being powered by AI, and agents play a crucial role in that. An AI agent is a computer program that uses a large language model as its "brain" to solve a given task. But what does that actually mean? Let's figure it out!

We'll start from scratch and see how to communicate with an LLM in Kotlin. We’ll be using Koog, a framework developed by JetBrains. Let’s add the Koog dependencies we need: 

```kotlin
// libs.versions.toml
[versions]
kotlin = "2.1.21"
koog = "0.4.0"

[libraries]
koog-agents = { module = "ai.koog:koog-agents", version.ref = "koog" }
koog-tools = { module = "ai.koog:agents-tools", version.ref = "koog" }
koog-executor-openai-client = { module = "ai.koog:prompt-executor-openai-client", version.ref = "koog" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }

// build.gradle.kts
dependencies {
    implementation(libs.koog.agents)
    implementation(libs.koog.tools)
    implementation(libs.koog.executor.openai.client)
}
```

We're using a version catalog to manage dependencies, but you can also add them directly to the script. Use the latest versions of Kotlin and Koog.

### Sending a request to an LLM

First, we’ll create an LLM client to handle the connection between our application and the chosen LLM provider. We’ll use OpenAI models, and need to specify our API key. You can copy it into a String directly, but it's better to store it in an environment variable:

```kotlin
val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))
```

Next, let's choose the model we want to use. In our case, it's GPT4_1Mini, a cost-effective model that's sufficient for our needs:

```kotlin
val model = OpenAIModels.CostOptimized.GPT4_1Mini
```


Koog abstracts the details of communication with LLMs. To switch model providers, you simply create a different LLM client, such as an Anthropic one, and choose a different model, such as Claude Sonnet:


```kotlin
val anthropicClient = AnthropicLLMClient(System.getenv("ANTHROPIC_API_KEY"))
val anthropicModel = AnthropicModels.Sonnet_4
```

All clients implement the `LLMClient` interface. A single client can work with many models – you can pick up a model for each interaction.

Next, we need to create a prompt, specify its ID, and pass in arguments. One of the key ones is temperature; we can use 0.7—a good balance between creativity and consistency. Let’s ask the LLM to translate the user input into German:

```kotlin
val prompt = prompt(
   id = "translation-request",
   params = LLMParams(temperature = 0.7)
) {
   user("Translate into German: ${readln()}")
}
```

We connect to the LLM by sending our prompt and passing the model as an argument. Since executing an LLM request is a suspend function, we run it inside the runBlocking call:

```kotlin
val message = executor.execute(prompt, model)
println(message.content)
println(message.metaInfo)
```

The result is a list of Responses. In our case, it’s one element, and we print out its content, along with the meta information attached to it.

You can run the code and enter the input, like “You can develop AI Agents in Kotlin!” After waiting a bit, it should return the translated line and some meta information, including a timestamp and total, input, and output token count.


### Implementing a Tool Use by Hand

Exchanging text messages alone is limiting. Let’s teach the LLM to interact with the real world—like sending money—by calling a tool. Of course, it can’t send money by itself; it can only return text. But we can teach it to trigger a “send money” action based on the user’s input.

If you’re new to the idea, let’s quickly build a tool interaction by hand. We’ll have the model use JSON syntax we provide, so we can see exactly how it works—then we’ll redo it using Koog’s built-in tool support.

We add a system message instructing an LLM to behave like a banking assistant. We say it can send money by writing a specific JSON, and provide its structure.


```kotlin
val promptForTool = prompt(
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
   user("Send 20 euros to Daniel for dinner at the restaurant")    
}
```

We also provide a user message. If you run it, the model should return the correct JSON, like this:

```
{ 
    "name": "send_money",
    "params": {
        "recipient": "Daniel",
        "amount": 20,
        "purpose": "dinner at the restaurant"
    }
}
```

We could parse it and run the function, but we won’t do it in this tutorial. Modern LLMs support tools out of the box, and frameworks like Koog handle the implementation details for different providers.


### Workflow with Tool

A tool is any function the LLM can call based on your description. Under the hood, it uses special syntax LLMs were trained on, and the exact format varies between providers. You don’t need to know the details—Koog lets you use almost any Kotlin function as a tool.

Let’s walk through an example of sending money, step by step.

![Tools Send Money](images/tools-send-money.png)

1. User says: “Send 20 euros to Daniel”.
2. We send this request to the LLM, along with the list of available tools—in our case, just one: send money.
3. The LLM processes the input and returns the chosen tool name along with its arguments. That’s called a tool call.
4. From there, we can invoke the tool, calling any function in our environment.
5. For example, show a confirmation screen, and if the user agrees, perform the transfer.

Note that the large language model doesn’t run the tool itself—it just picks one. We invoke it on our side. We’re getting closer to the idea of an agent—a model that uses tools in a loop, based on feedback from the environment.

First, let’s implement the workflow with a single `sendMoney` tool in Koog.


### Kotlin Functions as Tools

Koog gives you a few ways to define tools, but we’ll go with the easiest—just add the `@Tool` annotation to a function. That’s it! Koog now knows this function is a tool, and it can send its signature and description to the LLM.

Our `sendMoney` function will have three parameters: amount, recipient, and purpose. All parameters and return types must be serializable so Koog can send them to the model and deserialize the response. For simplicity and reliability, it might be good to stick to basic types like strings or numbers.


```kotlin
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
```


To keep this example small, we'll just ask the user for confirmation in the console. The LLM only uses the result of the tool call as input—it doesn’t know or care whether the money was actually sent or how the function was implemented. Always return a descriptive result from the tool call to the LLM, even if the tool is primarily used for its side effects.

We need to include `LLMDescription` annotations with the corresponding descriptions. Koog will send an LLM a tool function signature that includes its name, parameter types, and return type, together with function and parameter descriptions.


### Basic Agent Using a Tool

Next, let’s create an agent that can call this tool. We’ll use Koog’s implementation: the `AIAgent` class. It runs prompts, manages tools, and can set up additional features:


```kotlin
val agent = AIAgent(...)
```


We need to pass in the Koog `PromptExecutor`, which acts as a higher-level abstraction for model interaction. While `LLMClient` is a low-level layer for communicating with different LLMs, the `PromptExecutor` builds on top of it, allowing you to add things like monitoring and logging. A single executor can manage multiple `LLMClient`s—useful when your code interacts with different model providers. In our case, we use `SingleLLMPromptExecutor`, taking the client as an argument:


```kotlin
val client = OpenAILLMClient(System.getenv("OPENAI_API_KEY"))
val executor = SingleLLMPromptExecutor(client)
```

Since we only use the OpenAI model, we can alternatively create a `simpleOpenAIExecutor` directly; it’s the same code as above:

```kotlin
val executor = simpleOpenAIExecutor(System.getenv("OPENAI_API_KEY"))
```

Next, we need to pass in a tool. The agent expects a tool registry, which typically contains multiple tools. The `ToolRegistry` is like a hub for all the tools an agent can use. It lets you register new tools and easily get them later by name or type. To create it, we call `ToolRegistry`, and we add a tool using the `tool()` function. You can pass a reference to a function annotated with `@Tool`:


```kotlin
val toolRegistry = ToolRegistry {
   tool(::sendMoney)
}
```


Let’s pass the executor and the tool registry to our agent and add a system prompt instructing a model to behave like a banking assistant:


```kotlin
val agent = AIAgent(
   executor = executor,
   llmModel = model,
   systemPrompt = "You're a banking assistant. Accompany the user with their request.",
   toolRegistry = toolRegistry
)
```

Then, when we run the agent, we pass the user message “send 25 euros to Daniel” as an argument. Let’s output the result:

```kotlin
val message = "Send 25 euros to Daniel for dinner at the restaurant."
val result = agent.run(message)
println("Final result:")
println(result)
```

When we run this code, we can see that our tool, the `sendMoney` function, gets called! We see the *“Please confirm the transaction”* message from the `sendMoney` implementation:

```
=======
Sending 25 EUR to Daniel with the purpose: "dinner at the restaurant".
Please confirm the transaction by entering 'yes' or 'no'.
=======
```

Suppose we confirm by entering "Yes," and the LLM responds that the transfer was successful:

```
=======
Yes
Final result:
I have sent 25 euros to Daniel for dinner at the restaurant. If you need anything else, feel free to ask!
```

To better understand how all of this works, let’s track the request and response from the LLM by adding an event handler. We could also use proper tracing and logging, of course, that’s, btw, why you see the red error messages – usually, you provide a logger. But to avoid overly verbose logs, we're using this approach in the tutorial.

Let’s add the `event-handler` dependency—both to the version catalog and to the  script:

```kotlin
// libs.versions.toml
koog-features-event-handler = { module = "ai.koog:agents-features-event-handler", version.ref = "koog" }

// build.gradle.kts
implementation(libs.koog.features.event.handler)
```

`AIAgent` takes an `installFeatures` lambda as its last argument. This allows you to install features like tracing, agent memory, or—like in our case—an event handler. Typically, you call `install` and pass the feature name—for example: `install(EventHandler)`.  But for this case, we also have a convenient extension called `handleEvents {...}`:


```kotlin
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
```


We want to handle two types of events: “Before the LLM call”, where we’ll print out the messages and tools, and “After the LLM call”, where we’ll print the LLM’s response.

Run the code again.

1. You can see the system and user messages, along with the `sendMoney` tool description, in the initial request.
2. Then, you can observe that the LLM's response is indeed a tool call, and it includes all the argument values.
3. Koog calls the tool—you see the console output and can enter the confirmation.
4. Next, Koog sends the result of the tool call back to the LLM as the next request.
5. Finally, the LLM responds with the *“It’s done” *type of message.

Tools are often described as functions the LLM can use to give you the result you need. But you can also provide the LLM with a tool to call for its side effects—and in some cases, the tool invocation is the result. In this example, the LLM returns a final confirmation, but the main action was actually performed during the tool call.

Let’s now return to the general flow of a basic agent with tool calls:

![Tools General Flow](images/tools-general-flow.png)

1. The user sends a prompt to the agent.
2. The agent calls the LLM—it might resend the same prompt along with the available tools, or it might add some additional context.
3. The LLM returns a response, which could be either a text message or a tool call.
4. If it’s a tool call, the agent can invoke the tool.
5. It gets the result from the environment, which it then sends back to the LLM.
6. This process goes on until the LLM no longer wants to call any tools and reaches the final answer.

**A basic agent is a model that uses tools in a loop, based on feedback from the environment.**

We say that such an agent follows a universal strategy: simply give the LLM the task and all the available tools. The LLM kind of becomes the “brain”—it decides on its own which tools to call and in what order. Let’s add more tools to our example to see this in action.

### Giving Several Tools to LLM

Usually, we want to send money to a specific user—not just an unknown “Daniel.” To handle that, we can give the LLM access to the user’s contacts. And if the recipient is unclear—say, we have two contacts named Daniel—we can provide another tool that asks the user to choose the correct one.

So now we have three tools: `getContacts`, `chooseRecipient`, and `sendMoney`. The `sendMoney` tool now requires the contact's ID, not just their name.

![Three Tools](images/three-tools.png)

Let’s implement a Contact class, containing id, name, last name, and a phone number:

```kotlin
@Serializable
data class Contact(val id: Int, val name: String, val lastName: String? = null, val phoneNumber: String? = null)
```

Then, add some mock-up data:

```kotlin
val contactList = listOf(
   Contact(100, "Alice", "Smith", "+1 415 555 1234"),
   Contact(101, "Bob", "Johnson", "+49 151 23456789"),
   Contact(102, "Charlie", "Williams", "+36 20 123 4567"),
   Contact(103, "Daniel", "Anderson", "+46 70 123 45 67"),
   Contact(104, "Daniel", "Garcia", "+34 612 345 678"),
)
```

Now that we have several tools, let’s define them all in a `MoneyTransferTools` class. This class should implement the `ToolSet` interface:

```kotlin
@LLMDescription("Tools for money transfer operations")
class MoneyTransferTools : ToolSet {
    fun sendMoney() { ... }
    fun getContacts() { ... }
    fun chooseRecipient() { ... }
}
```

The sendMoney implementation stays almost the same; it only takes a recipientId instead of their name. Also, let’s add senderId as another parameter. Since we have it in our mock data, let’s display all the recipient contact details: their name, last name and phone:

```kotlin
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
```

Next, let’s implement the getContacts function, which returns a list of contacts. Since LLMs work with text, we can simply send the list as a String:

```kotlin
val contactMap = contactList.associateBy { it.id }

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
```


Next, the last tool to implement – choose the recipient. It finds all the contacts that could match a confusing recipient name and asks the user to choose the correct one. The result is an ID of the selected contact and a descriptive message if nothing was found or selected:

```kotlin
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
       println("No contact named $confusingRecipientName was found. Here are all available contacts--please choose one:")
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
```

You often have a choice—give the model information as a tool or include it in the context, like in a system or user prompt. For example, we could list all the contacts in the prompt, or create a “filter contacts by name” tool so we don’t have to send the whole list. It’s worth experimenting to see what works best for your case. For this demo, we’ll go with three tools.

Let’s now add all of these tools to our agent’s tool registry. We can do that in one line by calling `tools()` and passing the MoneyTransferTools instance:

```kotlin
val toolRegistry = ToolRegistry {
   tools(MoneyTransferTools().asTools())
}
```

The tools() function adds all the annotated methods defined in a given ToolSet as separate tools to a registry. It expects a `ToolSet` as a parameter, so that’s why it’s important for `MoneyTransferTools` to implement `ToolSet`.

Let's see step-by-step what happens if we run our agent:

* The user sends a request.
* The agent forwards this request—along with the list of all the available tools—to the LLM.
* The LLM first decides to get all the available contacts.
* The agent calls the tool.
* The environment returns the list of contacts.
* The agent then sends the tool result along with the previous conversation history, so the LLM understands the context.
* The model sees that there are two contacts named Daniel, so it decides to call the "chooseRecipient" tool.
* That tool requires user input.
* The user confirms their choice, and we send the result back to the LLM, once again including the full history.
* Finally, the LLM decides to call the `sendMoney` tool, providing the correct recipient ID as an argument.

You can observe that in action. Since we have the event handlers installed, you can check the requests to LLM and its responses. 

### Conversation History

We always send the LLM the entire conversation history:

![Message History](images/message-history.gif)

* At first, it’s just the system and user messages, along with a separate list of available tools.
* Then, the LLM decides to call a tool, and we append the result.
* It calls another tool; we append that result too.
* And then yet another.

If you only send the last message, such as a tool call result, it won’t remember what your original request was! The good news is—Koog handles it automatically for you. Each time, when LLM decides to call a tool, Koog calls it and appends the result to the list of messages.

By the way, Koog also lets you optimize the message history using different history compression approaches.

### Basic Agent and Beyond

So now you understand what an agent with a universal strategy is: a model calling tools in a loop. We give the model the available tools, let it decide which ones to use, call those tools to get results, and send the results back to the model. That’s it!

You can think of it like this: “the LLM is the brain”. It makes all the decisions based on your input. You don’t know which tools it’s going to call or in what order. That’s exactly what we saw in our example: the LLM had a list of tools and decided on its own which ones to use and when. It was able to take the result of one of the tool calls—like the recipient ID—and use it as input for the next one.

You can ask a model to solve a pretty complicated task, and only provide a list of tools, using a basic universal agent we just discussed. It works surprisingly well – try it yourself, *it’s all you need* to start creating your own agents! Define tools, pick up the model, ask it to complete your task with the provided tools. For a prototype, it’s more than enough – you can see what LLMs are capable of, and what their constraints are for your specific use case.

However, such a universal strategy of providing a high-level task and all the available tools no longer works when the complexity grows, when you start to productize your functionality. Especially if you have a lot of tools, you often end up using different models and different sets of tools for different subtasks. Koog lets you orchestrate how those subtasks are connected to each other.

Think of it from the perspective of how much autonomy you want to give LLMs. There’s no one-size-fits-all—it depends on your use case. As a developer, you figure out which tasks the LLM can handle on its own and which need to be broken down and coded explicitly. The ability to move this LLM autonomy slider and to find that sweet spot for your requirements is crucial for real-life production scenarios. And Koog gives you this flexibility.

It does so much more: memory, persistence, history compression, advanced agent strategies, tracing, observability—everything you need to run AI agents in production. 

You’re now ready to start building your first Kotlin agent—let’s go!