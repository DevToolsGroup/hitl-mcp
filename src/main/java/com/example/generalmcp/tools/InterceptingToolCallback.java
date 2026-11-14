package com.example.generalmcp.tools;

import com.example.generalmcp.history.HistoryRecord;
import com.example.generalmcp.history.HistoryRecord.Status;
import com.example.generalmcp.history.HistoryWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.time.Instant;
import java.util.UUID;

/**
 * Decorator around a real tool. Every invocation passes through here, so this is the single place
 * where cross-cutting behaviour is applied.
 */
@RequiredArgsConstructor
public class InterceptingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final HistoryWriter historyWriter;

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String id = UUID.randomUUID().toString();
        String toolName = delegate.getToolDefinition().name();
        Instant startedAt = Instant.now();
        try {
            String result = toolContext == null ? delegate.call(toolInput) : delegate.call(toolInput, toolContext);
            historyWriter.append(new HistoryRecord(id, toolName, startedAt, Instant.now(),
                    Status.COMPLETED, null, toolInput, result));
            return result;
        } catch (RuntimeException e) {
            historyWriter.append(new HistoryRecord(id, toolName, startedAt, Instant.now(),
                    Status.FAILED, null, toolInput, String.valueOf(e.getMessage())));
            throw e;
        }
    }
}
