package com.unitel.laoads.infra.vsa;

import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CasVsaClientTest {

    private static byte[] xml(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesAuthenticationSuccess() {
        String body = """
                <cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'>
                  <cas:authenticationSuccess>
                    <cas:user> tungpm </cas:user>
                    <cas:attributes><cas:email>t@unitel.la</cas:email></cas:attributes>
                  </cas:authenticationSuccess>
                </cas:serviceResponse>""";
        assertThat(CasVsaClient.parseServiceResponse(xml(body))).isEqualTo("tungpm");
    }

    @Test
    void authenticationFailureIsUnauthorized() {
        String body = """
                <cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'>
                  <cas:authenticationFailure code="INVALID_TICKET">Ticket ST-1 not recognized</cas:authenticationFailure>
                </cas:serviceResponse>""";
        assertThatThrownBy(() -> CasVsaClient.parseServiceResponse(xml(body)))
                .isInstanceOfSatisfying(LaoadsException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void doctypeRejected() {
        String body = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'><cas:authenticationSuccess>"
                + "<cas:user>&e;</cas:user></cas:authenticationSuccess></cas:serviceResponse>";
        assertThatThrownBy(() -> CasVsaClient.parseServiceResponse(xml(body)))
                .isInstanceOfSatisfying(LaoadsException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ErrorCode.VSA_UNAVAILABLE));
    }

    @Test
    void loginUrlEncodesServiceAndDisabledReturnsNull() {
        CasVsaClient enabled = new CasVsaClient(new VsaProperties(true, "https://vsa.example.la/cas/",
                "http://localhost:5173/auth/vsa/callback?x=1", Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertThat(enabled.isEnabled()).isTrue();
        assertThat(enabled.loginUrl()).isEqualTo(
                "https://vsa.example.la/cas/login?service=http%3A%2F%2Flocalhost%3A5173%2Fauth%2Fvsa%2Fcallback%3Fx%3D1");

        CasVsaClient noUrl = new CasVsaClient(new VsaProperties(true, " ", null, null, null));
        assertThat(noUrl.isEnabled()).isFalse();
        assertThat(noUrl.loginUrl()).isNull();
    }
}
