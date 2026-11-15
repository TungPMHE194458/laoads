package com.unitel.laoads.web.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DevDatabaseGuardTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:oracle:thin:@//localhost:1521/XE",
            "jdbc:oracle:thin:@localhost:1521:XE",
            "jdbc:oracle:thin:@//127.0.0.1:1/none",
            "jdbc:oracle:thin:@tcp://LOCALHOST:1521/XE",
            "jdbc:oracle:thin:@//[::1]:1521/XE",
            "jdbc:oracle:thin:user/p@ss@//localhost:1521/XE",
            "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=TCP)(HOST=localhost)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=XE)))",
    })
    void localUrls(String url) {
        assertThat(DevDatabaseGuard.isLocal(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:oracle:thin:@//10.20.30.40:1521/shared_db",
            "jdbc:oracle:thin:@10.20.30.40:1521:shared",
            "jdbc:oracle:thin:@//localhost.evil.com:1521/XE",
            "jdbc:oracle:thin:@shared_db",
            "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS_LIST=(ADDRESS=(HOST=localhost)(PORT=1521))(ADDRESS=(HOST=10.20.30.40)(PORT=1521))))",
            "not-a-jdbc-url",
    })
    void remoteOrUnknownUrls(String url) {
        assertThat(DevDatabaseGuard.isLocal(url)).isFalse();
    }

    @Test
    void localDbPasses() {
        var env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:oracle:thin:@//localhost:1521/XE")
                .withProperty("laoads.read-only-datasource.jdbc-url", "jdbc:oracle:thin:@//localhost:1521/XE");
        assertThatCode(() -> new DevDatabaseGuard(env).check()).doesNotThrowAnyException();
    }

    @Test
    void sharedDbBlockedEvenIfOnlyReadOnlyPool() {
        var env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:oracle:thin:@//localhost:1521/XE")
                .withProperty("laoads.read-only-datasource.jdbc-url", "jdbc:oracle:thin:@//10.20.30.40:1521/shared_db");
        assertThatThrownBy(() -> new DevDatabaseGuard(env).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("10.20.30.40")
                .hasMessageContaining("LAOADS_DEV_ALLOW_REMOTE_DB");
    }

    @Test
    void sharedDbAllowedWithExplicitFlag() {
        var env = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:oracle:thin:@//10.20.30.40:1521/shared_db")
                .withProperty("laoads.dev.allow-remote-db", "true");
        assertThatCode(() -> new DevDatabaseGuard(env).check()).doesNotThrowAnyException();
    }
}
