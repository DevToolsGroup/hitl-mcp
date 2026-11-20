# HITL MCP Server

HITL MCP Server is a Java library for building [MCP](https://modelcontextprotocol.io) servers
where a human reviews every tool call and its result.
HITL stands for "human in the loop".

## What is it?

An AI agent connected to an MCP server can call any tool the server publishes,
and receives whatever the tool returns.
This library puts a person between the two.
Before a tool runs, the person sees its name and arguments in a browser page and accepts or denies the call.
After the tool runs, the person sees the result and decides what the agent gets:
the result as is, a result they edited, or a denial with an optional reason.

You write the tools as ordinary Spring AI `@Tool` methods.
The library wraps each of them, publishes them over MCP (Streamable HTTP, endpoint `/mcp`),
and serves the review page from the same application.
The tools themselves know nothing about reviews.

## Features

- **Two reviews per call.** The input is reviewed before the tool runs, and the result before it goes back to the agent.
- **Editable results.** The reviewer can replace the result with their own text before the agent sees it.
- **Denial reasons.** A short explanation can be sent to the agent along with a denial.
- **Parallel calls.** Several calls can wait at once, and the reviewer decides on them in any order.
- **Long texts.** Large inputs and results are paginated on the review page.
- **History.** Every finished call is appended to a JSON Lines file, with its status, input and result.
- **Two ways to start.** It works as a dependency of a Spring Boot application or from a plain `main` method.

## A minimal example

A class of tools is annotated with `@HitlMcpTools`, and its methods with Spring AI's `@Tool`:

```java
import org.devtoolsgroup.hitlmcp.HitlMcpServer;
import org.devtoolsgroup.hitlmcp.HitlMcpTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@HitlMcpTools
public class CalculatorTools {

    @Tool(name = "add", description = "Adds two numbers and returns the sum")
    public double add(@ToolParam(description = "First addend") double a,
                      @ToolParam(description = "Second addend") double b) {
        return a + b;
    }

    public static void main(String[] args) {
        HitlMcpServer.builder()
                .tools(new CalculatorTools())
                .port(8080)
                .start(args);
    }
}
```

On startup the server prints its URLs.
Open the review page at `http://127.0.0.1:8080/mcp-review/`
and point your agent at `http://127.0.0.1:8080/mcp`, as described in [Connecting an AI agent](#connecting-an-ai-agent).

Each call the agent makes now appears as a card on the page and waits for your decision.

In a Spring Boot application you don't need the builder.
Add the dependency, and the auto-configuration finds the `@HitlMcpTools` classes through component scan.

## Connecting an AI agent

A tool call returns only after you have decided on it, and a review can take a long time.
If the server is registered in the agent's configuration the usual way,
the agent's built-in MCP client may give up waiting before you decide, and the call fails with a timeout.
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

## Configuration

The library sets its own defaults, and any of them can be overridden in the application's configuration
or with `HitlMcpServer.builder().property(name, value)`.

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | HTTP port, `0` picks a free one |
| `hitlmcp.ui.page-size` | `100000` | Characters of an input or result shown on one page of a card |
| `hitlmcp.ui.max-message-chars` | `2000000` | Characters of a decision the review page may send, with the updated result and reason |
| `hitlmcp.review.updated-result-prefix` | `the result was modified by the reviewer: ` | Put before a result the reviewer replaced; empty for none |
| `hitlmcp.history.file` | `log/call-history.jsonl` | History file |
| `hitlmcp.history.input-max-chars` | `100000` | Characters of the input stored in history |
| `hitlmcp.history.output-max-chars` | `100000` | Characters of the result stored in history |

## Learn more

The examples in `src/test/java/org/devtoolsgroup/hitlmcp/example/` show both kinds of applications.
Run them with:

```bash
# Spring Boot application
mvn spring-boot:test-run
# Plain Java application
mvn spring-boot:test-run -Dspring-boot.run.main-class=org.devtoolsgroup.hitlmcp.example.ExamplePlainJavaApplication
```

## Requirements

- Java 21 or newer.
- A Spring Boot application using the library must be on Spring Boot 4 and Spring AI 2.
  A plain Java application gets them as transitive dependencies.
- Compile the tools with `-parameters`, otherwise the tool arguments are named `arg0`, `arg1`, ...
  Applications using `spring-boot-starter-parent` have it already.

## Installation

The library is not published to a Maven repository yet.
Install it into your local repository:

```bash
mvn install
```

Then add the dependency:

```xml
<dependency>
    <groupId>org.devtoolsgroup</groupId>
    <artifactId>hitl-mcp-server</artifactId>
    <version>0.0.6-SNAPSHOT</version>
</dependency>
```

## License

[MIT](LICENSE.txt)
