# MCP server with a human in the loop

## What this project is

This is an MCP (Model Context Protocol) server that an AI agent connects to over HTTP.
Its distinguishing feature is that a human reviews every tool invocation twice:

1. **Before execution.**
   The user sees the tool name and the input arguments,
   and either accepts the call or denies it with an optional reason.
2. **After execution.**
   The user sees the result,
   and either lets it go back to the agent or withholds it with an optional reason.

The goal is to give the user full control over what the agent is allowed to do
and over what data the agent receives.

The user works in a browser page served by the same application.
The page receives updates from the server over a WebSocket.

## Scope and constraints

- Single user, no authentication.
- Reachable from localhost only.
- Several tool invocations may run in parallel.
  The user sees all of them at once and can decide on any of them in any order.
- A pending invocation waits for the user forever, there is no timeout.
- The tools themselves are simple demo tools (adding and multiplying numbers).
  The approval mechanism does not depend on the tools and works for any tool added later.
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
The files are served by Spring Boot from `src/main/resources/static/`.
Only built-in browser APIs are used: `WebSocket`, `JSON` and the DOM.

## Architecture

```text
 AI agent ──HTTP /mcp──► Spring AI MCP server
                              │
                              ▼
                     InterceptingToolCallback ◄─── decorator around every tool
                        │            │
                        │            ▼
                        │      ReviewRegistry ──WebSocket /ws──► browser page
                        │            ▲                              │
                        │            └──────── decisions ───────────┘
                        ▼
                   real tool (e.g. add, multiply)
                        │
                        ▼
                   HistoryWriter ──► history file (JSON Lines)
```

### Tool interception

Every Spring AI `ToolCallback` is wrapped in a decorator before it is registered with the MCP server.
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
5. Return the real result to the agent.
6. Append a record to the history file and remove the entry from the page.

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
If the server is stopped while a call is waiting, the call ends with the `CANCELLED` status.

### WebSocket protocol

All messages are JSON objects with a `type` field.

Server to browser:

| Type | Payload | Meaning |
|---|---|---|
| `snapshot` | list of reviews, page size N | Sent on connect, so a page refresh or a late connection loses nothing |
| `upsert` | one review | A review was created or changed its phase |
| `remove` | review id | A review is finished |

Browser to server:

| Type | Payload | Meaning |
|---|---|---|
| `decision` | `id`, `phase`, `approved`, `reason` | The user pressed Accept or Deny |

### User interface

The page shows one card per pending invocation.
A card contains:

- the phase: "Call request" or "Result review";
- the tool name and the time of the call;
- the input arguments, pretty-printed, and the result once it is available;
- a text area for the reason of denial;
- the **Accept** and **Deny** buttons.

Inputs and outputs can be very long, so each text block on a card is paginated.
A page shows at most N characters, where N is configurable.
N counts characters of the displayed text, that is, after JSON pretty-printing.
A page boundary never splits an emoji or another character made of two UTF-16 code units.
The page controls are minimal: previous page, `current / total`, next page.
They are hidden when the text fits on one page.

The page reconnects automatically if the WebSocket connection is lost.

### History file

Every finished invocation is appended to a file in the JSON Lines format, one JSON object per line.
A record contains:

- invocation id, tool name, start and finish timestamps;
- final status: `COMPLETED`, `FAILED`, `CALL_DENIED`, `RESULT_DENIED` or `CANCELLED`;
- denial reason, if any;
- input, truncated to Ni characters, and its full length;
- output, truncated to No characters, and its full length.

The full lengths make it visible when a stored value was truncated.

### Security

- The server binds to `127.0.0.1` only, so it is not reachable from other machines.
- The `Origin` header is checked on `/mcp` and `/ws`.
  Only `http://localhost:<port>` and `http://127.0.0.1:<port>` are allowed,
  or no `Origin` at all for non-browser clients.
  This protects against DNS rebinding attacks from web pages opened in the same browser,
  as recommended by the MCP specification.

## Configuration

All settings live in `application.properties`:

| Property | Default | Meaning |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `server.address` | `127.0.0.1` | Bind address, localhost only |
| `app.ui.page-size` | `2000` | N, characters per page in a card |
| `app.history.file` | `log/call-history.jsonl` | Path of the history file |
| `app.history.input-max-chars` | `2000` | Ni, max characters of input stored in history |
| `app.history.output-max-chars` | `2000` | No, max characters of output stored in history |

## Connecting Claude Code

```bash
claude mcp add --transport http hitl-mcp http://localhost:8080/mcp
```

The review page is available at `http://localhost:8080/`.
