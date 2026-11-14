package org.devtoolsgroup.hitlmcp.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Demo tool producing long outputs, handy for trying out pagination on the review page.
 */
@Component
public class TextTools {

    static final int MAX_LENGTH = 1_000_000;
    private static final int MAX_WORD_LENGTH = 10;

    @Tool(name = "random_text",
            resultConverter = PlainTextResultConverter.class,
            description = "Generates random text of exactly the given number of characters: "
                    + "random lowercase words separated by spaces. Maximum length is " + MAX_LENGTH + ".")
    public String randomText(@ToolParam(description = "Number of characters to generate") int length) {
        if (length < 0 || length > MAX_LENGTH) {
            throw new IllegalArgumentException("length must be between 0 and " + MAX_LENGTH + ", got " + length);
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder text = new StringBuilder(length);
        while (text.length() < length) {
            if (!text.isEmpty()) {
                text.append(' ');
            }
            int wordLength = Math.min(1 + random.nextInt(MAX_WORD_LENGTH), length - text.length());
            for (int i = 0; i < wordLength; i++) {
                text.append((char) ('a' + random.nextInt(26)));
            }
        }
        return text.toString();
    }
}
