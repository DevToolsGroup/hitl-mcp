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

import org.devtoolsgroup.hitlmcp.history.HistoryRecord.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class HistoryWriterTest {

    private static final Instant STARTED = Instant.parse("2026-11-18T10:00:00Z");
    private static final Instant FINISHED = Instant.parse("2026-11-18T10:00:05.123Z");

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @TempDir
    Path tempDir;

    private HistoryWriter writer(Path file, int inputMaxChars, int outputMaxChars) {
        return new HistoryWriter(new HistoryProperties(file, inputMaxChars, outputMaxChars), jsonMapper);
    }

    private static HistoryRecord record(Status status, String denialReason, String input, String output,
                                        String updatedResult) {
        return new HistoryRecord("id-1", "sql", STARTED, FINISHED, status, denialReason, input, output,
                updatedResult);
    }

    private List<JsonNode> lines(Path file) throws Exception {
        return Files.readAllLines(file).stream().map(jsonMapper::readTree).toList();
    }

    @Test
    void writesOneJsonLinePerRecord() throws Exception {
        Path file = tempDir.resolve("history.jsonl");

        writer(file, 100, 100).append(record(Status.COMPLETED, null, "in", "out", null));

        assertThat(Files.readString(file)).endsWith(System.lineSeparator());
        JsonNode line = lines(file).getFirst();
        assertThat(line.get("id").asString()).isEqualTo("id-1");
        assertThat(line.get("tool").asString()).isEqualTo("sql");
        assertThat(line.get("startedAt").asString()).isEqualTo("2026-11-18T10:00:00Z");
        assertThat(line.get("finishedAt").asString()).isEqualTo("2026-11-18T10:00:05.123Z");
        assertThat(line.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(line.get("input").asString()).isEqualTo("in");
        assertThat(line.get("inputLength").asInt()).isEqualTo(2);
        assertThat(line.get("output").asString()).isEqualTo("out");
        assertThat(line.get("outputLength").asInt()).isEqualTo(3);
        assertThat(line.has("denialReason")).isFalse();
        assertThat(line.has("updatedResult")).isFalse();
        assertThat(line.has("updatedResultLength")).isFalse();
    }

    @Test
    void appendsToExistingFile() throws Exception {
        Path file = tempDir.resolve("history.jsonl");
        HistoryWriter writer = writer(file, 100, 100);

        writer.append(record(Status.COMPLETED, null, "first", "out", null));
        writer.append(record(Status.FAILED, null, "second", "err", null));

        assertThat(lines(file)).extracting(line -> line.get("input").asString()).containsExactly("first", "second");
    }

    @Test
    void multiLineValuesStayOnOneLine() throws Exception {
        Path file = tempDir.resolve("history.jsonl");

        writer(file, 100, 100).append(record(Status.COMPLETED, null, "select *\nfrom t", "a\r\nb", null));

        assertThat(Files.readAllLines(file)).hasSize(1);
        assertThat(lines(file).getFirst().get("input").asString()).isEqualTo("select *\nfrom t");
    }

    @Test
    void truncatesLongValuesAndKeepsFullLengths() throws Exception {
        Path file = tempDir.resolve("history.jsonl");

        writer(file, 3, 4).append(record(Status.COMPLETED, null, "abcdef", "0123456789", "updated result"));

        JsonNode line = lines(file).getFirst();
        assertThat(line.get("input").asString()).isEqualTo("abc");
        assertThat(line.get("inputLength").asInt()).isEqualTo(6);
        assertThat(line.get("output").asString()).isEqualTo("0123");
        assertThat(line.get("outputLength").asInt()).isEqualTo(10);
        assertThat(line.get("updatedResult").asString()).isEqualTo("upda");
        assertThat(line.get("updatedResultLength").asInt()).isEqualTo(14);
    }

    @Test
    void valuesAtTheLimitAreNotTruncated() throws Exception {
        Path file = tempDir.resolve("history.jsonl");

        writer(file, 3, 3).append(record(Status.COMPLETED, null, "abc", "xyz", null));

        JsonNode line = lines(file).getFirst();
        assertThat(line.get("input").asString()).isEqualTo("abc");
        assertThat(line.get("output").asString()).isEqualTo("xyz");
    }

    @Test
    void missingOutputHasZeroLength() throws Exception {
        Path file = tempDir.resolve("history.jsonl");

        writer(file, 100, 100).append(record(Status.CALL_DENIED, "no", "in", null, null));

        JsonNode line = lines(file).getFirst();
        assertThat(line.get("output").isNull()).isTrue();
        assertThat(line.get("outputLength").asInt()).isZero();
        assertThat(line.get("status").asString()).isEqualTo("CALL_DENIED");
        assertThat(line.get("denialReason").asString()).isEqualTo("no");
    }

    @Test
    void blankDenialReasonIsNotWritten() throws Exception {
        Path file = tempDir.resolve("history.jsonl");

        writer(file, 100, 100).append(record(Status.RESULT_DENIED, "   ", "in", "out", null));

        assertThat(lines(file).getFirst().has("denialReason")).isFalse();
    }

    @Test
    void createsMissingParentDirectories() throws Exception {
        Path file = tempDir.resolve("a/b/history.jsonl");

        writer(file, 100, 100).append(record(Status.COMPLETED, null, "in", "out", null));

        assertThat(lines(file)).hasSize(1);
    }

    @Test
    void writeFailureDoesNotBreakTheCall() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("not-a-file"));

        assertThatCode(() -> writer(directory, 100, 100).append(record(Status.COMPLETED, null, "in", "out", null)))
                .doesNotThrowAnyException();
    }
}
