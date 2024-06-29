package hudson.plugins.msbuild;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class MsBuildConsoleAnnotatorTest {

    private ByteArrayOutputStream out;
    private MSBuildConsoleAnnotator annotator;

    @Before
    public void setUp() {
        out = new ByteArrayOutputStream();
        annotator = new MSBuildConsoleAnnotator(out, StandardCharsets.UTF_8);
    }

    @Test
    public void testNumberOfWarnings() throws IOException {
        String warningMessage = "C:\\path\\to\\file(10,20): warning CS1234: This is a warning message";
        MockMSBuildWarningNote.PATTERN = Pattern.compile(".*warning.*");
        annotator.eol(warningMessage.getBytes(StandardCharsets.UTF_8), warningMessage.length());

        assertEquals(1, annotator.getNumberOfWarnings());
        assertEquals(0, annotator.getNumberOfErrors());
    }

    @Test
    public void testNumberOfErrors() throws IOException {
        String errorMessage = "C:\\path\\to\\file(10,20): error CS1234: This is an error message";
        MockMSBuildErrorNote.PATTERN = Pattern.compile(".*error.*");
        annotator.eol(errorMessage.getBytes(StandardCharsets.UTF_8), errorMessage.length());

        assertEquals(0, annotator.getNumberOfWarnings());
        assertEquals(1, annotator.getNumberOfErrors());
    }

    @Test
    public void testBothWarningsAndErrors() throws IOException {
        String warningMessage = "C:\\path\\to\\file(10,20): warning CS1234: This is a warning message";
        String errorMessage = "C:\\path\\to\\file(10,20): error CS1234: This is an error message";
        MockMSBuildWarningNote.PATTERN = Pattern.compile(".*warning.*");
        MockMSBuildErrorNote.PATTERN = Pattern.compile(".*error.*");

        annotator.eol(warningMessage.getBytes(StandardCharsets.UTF_8), warningMessage.length());
        annotator.eol(errorMessage.getBytes(StandardCharsets.UTF_8), errorMessage.length());

        assertEquals(1, annotator.getNumberOfWarnings());
        assertEquals(1, annotator.getNumberOfErrors());
    }

    @Test
    public void testMultipleCloseCalls() throws IOException {
        ByteArrayOutputStream mockOutputStream = new ByteArrayOutputStream();
        MSBuildConsoleAnnotator annotator = new MSBuildConsoleAnnotator(mockOutputStream, StandardCharsets.UTF_8);

        annotator.close();
        try {
            annotator.close(); // Trying to close again to see if it handles multiple closes gracefully
        } catch (IOException e) {
            fail("Should not throw an exception on multiple closes");
        }
    }

    private static class MockMSBuildWarningNote extends MSBuildWarningNote {
        @SuppressWarnings("unused")
        static Pattern PATTERN = Pattern.compile(".*warning.*");
    }

    private static class MockMSBuildErrorNote extends MSBuildErrorNote {
        @SuppressWarnings("unused")
        static Pattern PATTERN = Pattern.compile(".*error.*");
    }
}
