package com.example.testgenerator.execution;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaParserGeneratedTestValidatorTest {

    private final GeneratedTestValidator validator =
            new JavaParserGeneratedTestValidator();

    @Test
    void shouldAcceptValidJava() {

        String source = """
                package com.example;

                class ExampleTest {

                    void testSomething() {
                        int value = 1;
                    }
                }
                """;

        validator.validate(source);
    }

    @Test
    void shouldRejectInvalidJava() {

        String source = """
                package com.example;

                class ExampleTest {

                    void testSomething() {
                        int value =
                    }
                }
                """;

        assertThatThrownBy(
                () -> validator.validate(source)
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        "Generated test contains invalid Java syntax"
                );
    }
}