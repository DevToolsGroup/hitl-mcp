package org.devtoolsgroup.hitlmcp.example;

import org.devtoolsgroup.hitlmcp.HitlMcpServer;

/**
 * Example of a plain Java application using the library: no Spring annotations, the tools are passed explicitly.
 * Run with {@code mvn spring-boot:test-run -Dspring-boot.run.main-class=org.devtoolsgroup.hitlmcp.example.ExamplePlainJavaApplication}.
 */
public class ExamplePlainJavaApplication {

    public static void main(String[] args) {
        HitlMcpServer server = HitlMcpServer.builder()
                .tools(new CalculatorTools(), new TextTools())
                .port(8080)
                .start(args);
        System.out.println("Review page: http://localhost:" + server.getPort() + "/hitlmcp/");
    }
}
