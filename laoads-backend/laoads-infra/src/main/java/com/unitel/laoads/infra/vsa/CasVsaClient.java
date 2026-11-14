package com.unitel.laoads.infra.vsa;

import com.unitel.laoads.domain.auth.vsa.VsaClient;
import com.unitel.laoads.domain.common.ErrorCode;
import com.unitel.laoads.domain.common.LaoadsException;
import com.unitel.laoads.domain.common.Texts;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * VSA client chuẩn CAS 2.0: {@code GET {casUrl}/serviceValidate?ticket=&service=}, parse
 * {@code <cas:authenticationSuccess><cas:user>}. XML parser tắt DTD/entity ngoài (chống XXE).
 */
@Slf4j
public class CasVsaClient implements VsaClient {

    private final VsaProperties props;
    private final HttpClient http;

    public CasVsaClient(VsaProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public boolean isEnabled() {
        return props.enabled() && Texts.trimToNull(props.casUrl()) != null;
    }

    @Override
    public String loginUrl() {
        if (!isEnabled()) {
            return null;
        }
        String service = Texts.trimToNull(props.serviceUrl());
        String base = casBase() + "/login";
        return service == null ? base : base + "?service=" + encode(service);
    }

    @Override
    public String defaultServiceUrl() {
        return Texts.trimToNull(props.serviceUrl());
    }

    @Override
    public String validateTicket(String ticket, String service) {
        if (Texts.trimToNull(ticket) == null) {
            throw ticketInvalid();
        }
        URI uri = URI.create(casBase() + "/serviceValidate?ticket=" + encode(ticket.trim())
                + "&service=" + encode(service == null ? "" : service));
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(props.readTimeout())
                .header("Accept", "application/xml, text/xml")
                .GET()
                .build();
        HttpResponse<byte[]> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        } catch (Exception e) {
            throw unavailable(e);
        }
        if (resp.statusCode() != 200) {
            log.warn("[VSA] serviceValidate trả HTTP {}", resp.statusCode());
            throw unavailable(null);
        }
        return parseServiceResponse(resp.body());
    }

    /** Parse phản hồi CAS 2.0. Thành công → username; authenticationFailure / không có user → UNAUTHORIZED. */
    static String parseServiceResponse(byte[] xml) {
        Document doc;
        try {
            doc = secureBuilder().parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            log.warn("[VSA] Không parse được phản hồi CAS: {}", e.getMessage());
            throw unavailable(e);
        }
        NodeList success = doc.getElementsByTagNameNS("*", "authenticationSuccess");
        if (success.getLength() == 0) {
            NodeList failure = doc.getElementsByTagNameNS("*", "authenticationFailure");
            if (failure.getLength() > 0) {
                Element f = (Element) failure.item(0);
                log.warn("[VSA] Ticket bị từ chối: code={} msg={}", f.getAttribute("code"), f.getTextContent().trim());
            }
            throw ticketInvalid();
        }
        NodeList users = ((Element) success.item(0)).getElementsByTagNameNS("*", "user");
        String username = users.getLength() == 0 ? null : Texts.trimToNull(users.item(0).getTextContent());
        if (username == null) {
            throw ticketInvalid();
        }
        return username;
    }

    private static DocumentBuilder secureBuilder() throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f.newDocumentBuilder();
    }

    private String casBase() {
        String base = props.casUrl().trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static LaoadsException ticketInvalid() {
        return LaoadsException.withKey(ErrorCode.UNAUTHORIZED, "error.UNAUTHORIZED.vsaTicket",
                "Ticket VSA không hợp lệ hoặc đã hết hạn");
    }

    private static LaoadsException unavailable(Exception cause) {
        if (cause != null) {
            log.warn("[VSA] Không gọi được CAS: {}", cause.toString());
        }
        return new LaoadsException(ErrorCode.VSA_UNAVAILABLE, "Không kết nối được máy chủ VSA");
    }
}
