package org.devtoolsgroup.hitlmcp.history;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * @param file           history file, JSON Lines
 * @param inputMaxChars  how many characters of the tool input are stored
 * @param outputMaxChars how many characters of the tool output are stored
 */
@ConfigurationProperties("app.history")
public record HistoryProperties(Path file, int inputMaxChars, int outputMaxChars) {
}
