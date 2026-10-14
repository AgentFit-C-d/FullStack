package com.agentfit.coreapi.configuration.preview;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PreviewSensitiveContentTest {
    private PreviewInputFile file(String content) {
        return new PreviewInputFile("client", "config/settings.txt", content);
    }

    @Test
    void rejectsLiteralCredentialsBeforeCreatingGeneratedPreview() {
        List<String> cases = List.of(
            "api_key = very-secret-value\n",
            "{\"client_secret\":\"secret-value\"}",
            "password: hunter2\n",
            "TEAM_API_KEY=synthetic-secret-value\n",
            "export TEAM_TOKEN=synthetic-secret-value\n",
            "AWS_SECRET_ACCESS_KEY=synthetic-secret-value\n",
            "Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456\n",
            "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----",
            "token=ghp_123456789012345678901234567890123456\n",
            "url=https://name:password@example.org/path\n");
        for (int i = 0; i < cases.size(); i++) {
            String content = cases.get(i);
            int caseNumber = i;
            PreviewFileComparator.InvalidPreviewInputException error = assertThrows(
                PreviewFileComparator.InvalidPreviewInputException.class,
                () -> PreviewFileComparator.compare(List.of(file(content)), ExistingState.UNKNOWN, List.of()),
                "rejection case " + caseNumber);
            assertEquals("sensitive content detected", error.getMessage());
            assertFalse(error.getMessage().contains("hunter2"));
        }
    }

    @Test
    void rejectsProvidedOriginalBeforeBuildingDiff() {
        PreviewFileComparator.InvalidPreviewInputException error = assertThrows(
            PreviewFileComparator.InvalidPreviewInputException.class,
            () -> PreviewFileComparator.compare(List.of(file("api_key=${SERVICE_API_KEY}\n")),
                ExistingState.PROVIDED, List.of(file("api_key=old-secret\n"))));
        assertEquals("sensitive content detected", error.getMessage());
        assertThrows(PreviewFileComparator.InvalidPreviewInputException.class,
            () -> PreviewFileComparator.compare(List.of(file("TEAM_API_KEY=${TEAM_API_KEY}\n")),
                ExistingState.PROVIDED, List.of(file("TEAM_API_KEY=synthetic-old-value\n"))));
    }

    @Test
    void permitsEnvironmentReferencesPlaceholdersAndOrdinaryValues() {
        List<String> cases = List.of(
            "api_key=${SERVICE_API_KEY}\n",
            "TEAM_API_KEY=${TEAM_API_KEY}\n",
            "{\"client_secret\":\"$SERVICE_SECRET\"}",
            "password: <set-locally>\n",
            "api_key=\n",
            "permission=ASK_EACH_TIME\n",
            "url=https://example.org/path\n");
        for (int i = 0; i < cases.size(); i++) {
            String content = cases.get(i);
            List<PreviewFile> preview = assertDoesNotThrow(() -> PreviewFileComparator.compare(
                List.of(file(content)), ExistingState.UNKNOWN, List.of()), "allowed case " + i);
            assertEquals(content, preview.getFirst().content(), "allowed case " + i);
        }
    }
}
