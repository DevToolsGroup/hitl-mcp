# MCP server with a human in the loop

## What this project is

This is a Java library for building MCP (Model Context Protocol) servers that an AI agent connects to over HTTP.
An application adds the library as a dependency, implements its own tools,
and the library publishes them over MCP.
Its distinguishing feature is that a human reviews every tool invocation twice:

1. **Before execution.**
   The user sees the tool name and the input arguments,
   and either accepts the call or denies it with an optional reason.
2. **After execution.**
   The user sees the result,
   and either lets it go back to the agent or withholds it with an optional reason.
   When letting it go back, the user can replace the result with their own text.

The goal is to give the user full control over what the agent is allowed to do
and over what data the agent receives.

The user works in a browser page served by the same application.
The page receives updates from the server over a WebSocket.

## Scope and constraints

- Single user, no authentication.
- Reachable from localhost only.
- Several tool invocations may run in parallel.
  The user sees all of them at once and can decide on any of them in any order.
- A pending invocation waits for the user forever; there is no timeout.
- The library contains no tools of its own; the applications using it provide them.
  The approval mechanism does not depend on the tools.
- The examples in `src/test` have simple demo tools: adding and multiplying numbers,
  and generating random text of a given length (useful for trying out long outputs).
- Every finished invocation is appended to a history file.

## Technology

### Backend

| Concern | Choice |
|---|---|
| Language | Java 21 or newer |
| Framework | Spring Boot 4 |
| MCP protocol | Spring AI MCP Server (`spring-ai-starter-mcp-server-webmvc`), built on the official MCP Java SDK |
| MCP transport | Streamable HTTP, endpoint `/mcp` |
| Tool definition | Spring AI `@Tool` annotated methods |
| Browser updates | `spring-boot-starter-websocket`, a raw WebSocket handler (no STOMP, no SockJS) |
| JSON | Jackson (bundled with Spring Boot) |
| Boilerplate | Lombok |
| Build | Maven |

No database and no message broker are used.

### Frontend

Plain HTML, CSS and JavaScript, without any frameworks or libraries.
The files are served by Spring Boot from `src/main/resources/static/mcp-review/`, at `/mcp-review/`.
A separate folder keeps them from clashing with the application's own static files.
Only built-in browser APIs are used: `WebSocket`, `JSON` and the DOM.

The page has unit tests in `src/test/js/`.
They load the page into jsdom with a fake `WebSocket`,
play the server by sending messages and the user by clicking and typing,
and check the cards and the decisions the page sends.
They use Node's built-in test runner; jsdom is the only dependency, used by the tests only.
They are off by default.
`mvn test -DenableUiTests=true`, or `-Pui-tests`, runs them after the Java tests through `frontend-maven-plugin`,
which unpacks a pinned Node version into `node/` and ignores any Node installed on the machine.
In IntelliJ IDEA, tick the `ui-tests` profile in the Maven tool window.
`-DskipTests` skips them too.
To run only them: `PATH="$PWD/node:$PATH" npm test`, after one such Maven run has installed Node.

## Architecture

```text
 AI agent ──HTTP /mcp──► Spring AI MCP server
                              │
                              ▼
                     InterceptingToolCallback ◄─── decorator around every tool
                        │            │
                        │            ▼
                        │      ReviewRegistry ──WebSocket /mcp-review/ws──► browser page
                        │            ▲                              │
                        │            └──────── decisions ───────────┘
                        ▼
                   real tool (e.g. add, multiply)
                        │
                        ▼
                   HistoryWriter ──► history file (JSON Lines)
```

### Using the library

The library is a Spring Boot auto-configuration, so it works the same way in two kinds of applications.

Tools are Spring AI `@Tool` methods.
Only classes annotated with `@HitlMcpTools` are published; `@Tool` methods of other classes are ignored.
`@HitlMcpTools` is also a Spring `@Component`.

```java
@HitlMcpTools
public class JiraTools {

    @Tool(description = "Finds issues by JQL")
    public String search(@ToolParam(description = "JQL query") String jql) { ... }
}
```

A **Spring Boot application** only adds the dependency.
Its component scan finds the `@HitlMcpTools` classes, and the auto-configuration does the rest.

A **plain Java application** starts the server with a builder.
The builder runs Spring Boot internally, so the application needs no Spring code of its own.

```java
HitlMcpServer server = HitlMcpServer.builder()
        .tools(new JiraTools())                   // classes must be annotated with @HitlMcpTools
        .port(8080)                               // server.port, 0 picks a free port
        .historyFile("log/history.jsonl")         // hitlmcp.history.file
        .property("hitlmcp.ui.page-size", 5000)   // any other property
        .start(args);                             // returns once the server is up
// server.close() stops it
```

Things to know when using the library:

- Compile the tools with `-parameters` (`<maven.compiler.parameters>true</maven.compiler.parameters>`),
  otherwise the tool arguments are named `arg0`, `arg1`, ...
  Applications using `spring-boot-starter-parent` have it already.
- Publish tools only through `@HitlMcpTools` classes.
  Spring AI would publish other tools directly, bypassing the review:
  `ToolCallbackProvider` or `ToolCallback` beans, `SyncToolSpecification` beans,
  `@McpTool` methods when the annotation scanner is enabled.
  The library refuses to start when it finds such tools, see [Security](#security).
- A Spring Boot application must use the same Spring Boot and Spring AI generations as the library
  (Boot 4, Spring AI 2).
- A plain Java application gets Spring Boot, Tomcat and Jackson as transitive dependencies.

The library is installed into the local Maven repository with `mvn install`
and used as the dependency `org.devtoolsgroup:hitl-mcp-server:0.0.7-SNAPSHOT`.

The examples in `src/test/java/org/devtoolsgroup/hitlmcp/example/` show both kinds of applications:

```bash
# Spring Boot application
mvn spring-boot:test-run
# Plain Java application
mvn spring-boot:test-run -Dspring-boot.run.main-class=org.devtoolsgroup.hitlmcp.example.ExamplePlainJavaApplication
```

### Tool interception

Every `@Tool` method of a `@HitlMcpTools` bean becomes a Spring AI `ToolCallback`,
which is wrapped in a decorator before it is registered with the MCP server.
The real tools know nothing about approvals or history.
The decorator performs these steps for each invocation:

1. Create a review entry with the tool name and the input, and publish it to the browser.
2. Block until the user decides on the call.
   If the user denies it, return an error result to the agent:
   `Invocation denied by user. Reason: ...`.
3. Execute the real tool.
   If the tool throws an exception, the error text becomes the result to review.
   If the user accepts it, the agent receives the error.
4. Publish the result and block until the user decides on it.
   If the user denies it, return an error result to the agent:
   `Result withheld by user. Reason: ...`.
5. If the user entered an updated result, return it to the agent instead of the real result.
   This also applies when the tool failed: the agent receives the updated result, not the error.
   It is prefixed with `hitlmcp.review.updated-result-prefix`, so the agent knows the result was replaced.
   The history stores the updated result without the prefix.
   Otherwise, return the real result to the agent.
6. In every case, also after a denial or an error, remove the entry from the page
   and append a record to the history file.

Blocking is done with a `CompletableFuture` per decision.
Each MCP request runs on its own thread, so parallel invocations do not block each other.
Virtual threads are enabled to make many simultaneously waiting calls cheap.

### Review lifecycle

```text
AWAITING_CALL_APPROVAL ──accept──► EXECUTING ──► AWAITING_RESULT_APPROVAL ──accept──► COMPLETED
          │                                               │
          └──deny──► CALL_DENIED                          └──deny──► RESULT_DENIED
```

`ReviewRegistry` keeps the pending reviews in a `ConcurrentHashMap`.
A decision is applied only if it matches the current phase of the review.
That makes duplicate clicks, or clicks from a second browser tab, harmless: the first decision wins.

The server does not detect that the agent cancelled a request or disconnected.
Such a review stays on the page until the user decides on it; the answer then goes nowhere.

### WebSocket protocol

All messages are JSON objects with a `type` field.

Server to browser:

| Type | Payload | Meaning |
|---|---|---|
| `snapshot` | list of reviews, page size N, `maxMessageChars` M | Sent on connect, so a page refresh or a late connection loses nothing |
| `upsert` | one review | A review was created or changed its phase |
| `remove` | review id | A review is finished |

Browser to server:

| Type | Payload | Meaning |
|---|---|---|
| `decision` | `id`, `phase`, `approved`, `reason`, `updatedResult` | The user pressed an accept or deny button |

`updatedResult` is used only in the `AWAITING_RESULT_APPROVAL` phase and only when the result is accepted.
A missing or blank `updatedResult` means the real result goes back to the agent.

A message from the browser, JSON-encoded, may be at most M characters long,
so M also limits the length of an updated result.
The servlet container enforces the limit:
a longer message closes the connection with code 1009 ("message too big"), and the decision is lost.
Tomcat's own default is only 8192 characters, so the handler sets M on each review page session.
Other WebSocket endpoints of the application keep their own limits.
The container may reserve a buffer of M characters for each open review page.
The page checks the length before sending and tells the user, instead of sending a decision the server would reject.
If the connection is closed with code 1009 anyway, the page tells the user too.

### User interface

The page shows one card per pending invocation.
A card contains:

- the phase: "Call request", "Executing…" or "Result review";
- the tool name and the time of the call;
- the input arguments, pretty-printed, and the result once it is available;
- in the result review, a "Reviewer updated result" text area for a replacement of the result;
- a text area for the reason for denial;
- the accept and deny buttons, labelled by what they decide on:

  | Phase | Accept button | Deny button |
  |---|---|---|
  | Call request | **Accept input** | **Deny input** |
  | Result review | **Accept result** | **Deny result** |
  | Result review, "Reviewer updated result" not empty | **Accept updated result** | **Deny result** |

When the input or the result is a JSON object,
a dropdown to the right of its label lists the top-level attribute names.
The empty entry, selected initially, shows the whole JSON.
Selecting an attribute shows only its value.
A string value is shown as plain text, so line breaks and quotes are not escaped;
if the string is itself JSON, it is pretty-printed.
Other values are shown as pretty-printed JSON.

Inputs and outputs can be very long, so each text block on a card is paginated.
A page shows at most N characters, where N is configurable.
N counts characters of the displayed text, that is, after JSON pretty-printing.
A page boundary never splits an emoji or another character made of two UTF-16 code units.
The page controls are minimal: previous page, `current / total`, next page.
They are hidden when the text fits on one page.

The page reconnects automatically if the WebSocket connection is lost.

For quick UI testing, open the page with `?devmode=true` in the URL.
In dev mode the page does not connect to the server.
Instead, a test panel at the top drives one fake card:

- non-empty "Test input" creates the card in the call request phase, with that text as the input;
- non-empty "Test output" moves it to the result review, with that text as the result;
- the "Executing" checkbox moves it to the executing phase, whatever the output;
- clearing "Test input" removes the card.

The buttons do not change the card;
the decision message that would be sent to the server is printed to the browser console.

### History file

Every finished invocation is appended to a file in the JSON Lines format, one JSON object per line.
A record contains:

- invocation id, tool name, start and finish timestamps;
- final status: `COMPLETED`, `FAILED`, `CALL_DENIED`, `RESULT_DENIED` or `CANCELLED`;
- denial reason, if any;
- input, truncated to Ni characters, and its full length;
- output, truncated to No characters, and its full length;
- updated result, if the user replaced the result, truncated to No characters, and its full length.

The full lengths make it visible when a stored value was truncated.

### Security

- The server binds to `127.0.0.1` only, so it is not reachable from other machines.
- The application does not start if a tool would be published without review.
  After all beans are created, and before the web server accepts requests, `UnreviewedToolsCheck` verifies that:
  - every `ToolCallback` and `ToolCallbackProvider` bean contains reviewed tools only;
  - every tool the MCP server publishes is a reviewed one, whatever way it was registered.

  Tools of MCP servers the application is a client of are ignored,
  unless `spring.ai.mcp.server.expose-mcp-client-tools=true` makes Spring AI publish them.
- The `Origin` header is checked on `/mcp` and `/mcp-review/ws`.
  Only origins on `localhost`, `127.0.0.1` or `[::1]` are allowed,
  or no `Origin` at all for non-browser clients.
  This protects against DNS rebinding attacks from web pages opened in the same browser,
  as recommended by the MCP specification.

## Configuration

The library's defaults live in `hitlmcp-defaults.properties`.
They are loaded with the lowest precedence,
so the application overrides any of them in its own `application.properties`,
with environment variables, command line arguments or `HitlMcpServer.Builder.property(...)`.
A library cannot ship an `application.properties`, because the application's file of the same name would hide it.

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `server.address` | `127.0.0.1` | Bind address, localhost only |
| `hitlmcp.ui.page-size` | `100000` | N, characters per page in a card |
| `hitlmcp.ui.max-message-chars` | `2000000` | M, max characters of a message from the review page |
| `hitlmcp.review.updated-result-prefix` | `the result was modified by the reviewer: ` | Put before a result the reviewer replaced, empty for none |
| `hitlmcp.history.file` | `log/call-history.jsonl` | Path of the history file |
| `hitlmcp.history.input-max-chars` | `100000` | Ni, max characters of input stored in history |
| `hitlmcp.history.output-max-chars` | `100000` | No, max characters of output stored in history |

## Connecting an AI agent

When the web server is up, the library prints both URLs to the console,
for Spring Boot applications and for `HitlMcpServer.builder()` alike:

```text
HITL MCP server is running:
  MCP endpoint, to register with an AI agent: http://127.0.0.1:8080/mcp
  Review page, to open in a browser:          http://127.0.0.1:8080/mcp-review/
```

The URLs show the real address, port (also when picked at random with port `0`),
servlet context path and MCP endpoint.
They are written with `System.out`, so they are visible whatever logging configuration the application has.

A tool call returns only after the reviewer has decided on it, and a review can take a long time.
If the server is registered in the agent's configuration the usual way,
the agent's built-in MCP client may give up waiting before the reviewer decides, and the call fails with a timeout.
The preferred way is to ask the agent not to register the server,
but to send requests to it with `curl` or with a small script it writes, for example in Python.
That way the agent controls how long it waits for an answer.
An example prompt for the agent:

```text
An MCP server runs at http://127.0.0.1:8080/mcp.
A human reviews every tool call it receives, so an answer may take a long time.
Don't register it as an MCP server.
Instead, write a small script that connects to it and calls its tools,
and choose how long to wait for an answer based on the task.
```
