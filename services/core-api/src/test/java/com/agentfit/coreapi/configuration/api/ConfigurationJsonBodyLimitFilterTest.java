package com.agentfit.coreapi.configuration.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ConfigurationJsonBodyLimitFilterTest {
    private static final int LIMIT = 1_048_576;
    private final ConfigurationJsonBodyLimitFilter filter = new ConfigurationJsonBodyLimitFilter();

    @Test
    void exactLimitIsPassedToPreviewWithoutLosingTheRequestBody() throws Exception {
        byte[] body = new byte[LIMIT];
        var request = request("/api/projects/project-1/config-previews", body);
        var response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, (wrapped, ignored) -> {
            reached.set(true);
            assertEquals(LIMIT, wrapped.getInputStream().readAllBytes().length);
        });

        assertTrue(reached.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void chunkedOversizedPreviewIsRejectedBeforeController() throws Exception {
        byte[] body = new byte[LIMIT + 1];
        var request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setMethod("POST");
        request.setRequestURI("/api/projects/project-1/config-previews");
        request.setContent(body);
        var response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> reached.set(true));

        assertFalse(reached.get());
        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void oversizedDownloadReplayInputIsAlsoRejected() throws Exception {
        var request = request("/api/projects/project-1/config-approvals/approval-1/download",
            new byte[LIMIT + 1]);
        var response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> reached.set(true));

        assertFalse(reached.get());
        assertEquals(413, response.getStatus());
    }

    @Test
    void understatedContentLengthCannotBypassLimit() throws Exception {
        var request = new MockHttpServletRequest() {
            @Override public long getContentLengthLong() { return 1; }
        };
        request.setMethod("POST");
        request.setRequestURI("/api/projects/project-1/config-previews");
        request.setContent(new byte[LIMIT + 1]);
        var response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> reached.set(true));

        assertFalse(reached.get());
        assertEquals(413, response.getStatus());
    }

    private static MockHttpServletRequest request(String path, byte[] body) {
        var request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI(path);
        request.setContent(body);
        return request;
    }
}
