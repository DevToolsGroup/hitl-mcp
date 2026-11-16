/*
MIT License

Copyright (c) 2026-present DevToolsGroup (https://github.com/DevToolsGroup)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package org.devtoolsgroup.hitlmcp.history;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appends finished invocations to the history file, one JSON object per line.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryWriter {

    private final HistoryProperties properties;
    private final JsonMapper jsonMapper;

    public synchronized void append(HistoryRecord record) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("id", record.id());
        line.put("tool", record.tool());
        line.put("startedAt", record.startedAt().toString());
        line.put("finishedAt", record.finishedAt().toString());
        line.put("status", record.status());
        if (record.denialReason() != null) {
            line.put("denialReason", record.denialReason());
        }
        line.put("input", truncate(record.input(), properties.inputMaxChars()));
        line.put("inputLength", length(record.input()));
        line.put("output", truncate(record.output(), properties.outputMaxChars()));
        line.put("outputLength", length(record.output()));
        if (record.updatedOutput() != null) {
            line.put("updatedOutput", truncate(record.updatedOutput(), properties.outputMaxChars()));
            line.put("updatedOutputLength", length(record.updatedOutput()));
        }

        Path file = properties.file();
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, jsonMapper.writeValueAsString(line) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // History must never break a tool call.
            log.error("Cannot write history record {} to {}", record.id(), file, e);
        }
    }

    private static String truncate(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }
}
