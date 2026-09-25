package com.pulseops.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisParsingTest {

    private final AnalysisService service =
            new AnalysisService(null, null, null, null, null, null, new ObjectMapper(), new NoopTxManager());

    @Test
    void parsesStructuredReply() {
        var parsed = service.parseDiagnosis("""
                {"root_cause": "A traffic spike saturated the CPU.", "confidence": 72,
                 "suggested_actions": ["Check recent deploys", "Scale out"]}
                """);
        assertThat(parsed.rootCause()).isEqualTo("A traffic spike saturated the CPU.");
        assertThat(parsed.confidence()).isEqualTo(72);
        assertThat(parsed.actions()).containsExactly("Check recent deploys", "Scale out");
    }

    @Test
    void toleratesMarkdownFence() {
        var parsed = service.parseDiagnosis("```json\n{\"root_cause\": \"x\", \"confidence\": 10, \"suggested_actions\": []}\n```");
        assertThat(parsed.rootCause()).isEqualTo("x");
    }

    @Test
    void rejectsReplyWithoutRootCause() {
        assertThatThrownBy(() -> service.parseDiagnosis("{\"confidence\": 50}"))
                .isInstanceOf(GeminiClient.GeminiException.class);
        assertThatThrownBy(() -> service.parseDiagnosis("I think it is the database"))
                .isInstanceOf(GeminiClient.GeminiException.class);
    }

    /** AnalysisService builds a TransactionTemplate in its constructor; parsing never uses it. */
    static class NoopTxManager implements org.springframework.transaction.PlatformTransactionManager {
        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                org.springframework.transaction.TransactionDefinition definition) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status) {
        }

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status) {
        }
    }
}
