package com.agentfit.coreapi.domain.project;

import com.agentfit.coreapi.domain.project.service.PlanningInputValidator;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.ByteArrayOutputStream;

class PlanningInputValidationTest {
    private final PlanningInputValidator validator = new PlanningInputValidator();
    @Test void exactlyOneInputIsRequired() { assertThrows(IllegalArgumentException.class, () -> validator.validate("p",1,null,null)); assertThrows(IllegalArgumentException.class, () -> validator.validate("p",1,new MockMultipartFile("file","x.md","text/markdown","x".getBytes()),"x")); }
    @Test void directTextLimitIsMeasuredInCodePoints() { String text="a".repeat(100001); assertThrows(IllegalArgumentException.class, () -> validator.validate("p",1,null,text)); }
    @Test void markdownIsStoredWithSafeMetadata() { var input=validator.validate("p",1,new MockMultipartFile("file","plan.md","text/markdown","# plan".getBytes()),null); assertEquals("MARKDOWN",input.inputType()); assertEquals(6,input.byteSize()); assertEquals(6,input.characterCount()); }
    @Test void fileMustStayWithinTenMiBAndUseSupportedType() {
        byte[] tooLarge = new byte[10_485_761];
        assertThrows(IllegalArgumentException.class, () -> validator.validate("p", 1, new MockMultipartFile("file", "large.md", "text/markdown", tooLarge), null));
        assertThrows(IllegalArgumentException.class, () -> validator.validate("p", 1, new MockMultipartFile("file", "plan.txt", "text/plain", "x".getBytes()), null));
    }
    @Test void pdfPageLimitIsEnforced() throws Exception {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < 101; i++) document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            var out = new ByteArrayOutputStream(); document.save(out);
            var file = new MockMultipartFile("file", "plan.pdf", "application/pdf", out.toByteArray());
            assertThrows(IllegalArgumentException.class, () -> validator.validate("p", 1, file, null));
        }
    }
    @Test void unicodeCodePointLimitIsUsedForDirectText() {
        String text = "😀".repeat(100_001);
        assertThrows(IllegalArgumentException.class, () -> validator.validate("p", 1, null, text));
    }
}
