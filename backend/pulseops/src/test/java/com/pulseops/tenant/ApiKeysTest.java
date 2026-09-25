package com.pulseops.tenant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeysTest {

    @Test
    void generatedKeyHasPrefixAndMatchingHash() {
        ApiKeys.Generated key = ApiKeys.generate();
        assertThat(key.rawKey()).startsWith("pk_").hasSize(3 + 48);
        assertThat(key.prefix()).isEqualTo(key.rawKey().substring(0, 10));
        assertThat(key.hash()).hasSize(64).isEqualTo(ApiKeys.hash(key.rawKey()));
    }

    @Test
    void keysAreUnique() {
        assertThat(ApiKeys.generate().rawKey()).isNotEqualTo(ApiKeys.generate().rawKey());
    }

    @Test
    void hashIsDeterministic() {
        assertThat(ApiKeys.hash("pk_abc")).isEqualTo(ApiKeys.hash("pk_abc")).isNotEqualTo(ApiKeys.hash("pk_abd"));
    }
}
