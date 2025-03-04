package hudson.plugins.msbuild;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class MsBuildConsoleParserTest {

    private OutputStream mockOutputStream;
    private MsBuildConsoleParser parser;

    @BeforeEach
    void setUp() {
        mockOutputStream = mock(OutputStream.class);
        parser = new MsBuildConsoleParser(mockOutputStream, StandardCharsets.UTF_8);
    }

    @Test
    void testGetNumberOfWarnings() throws Exception {
        String line = "    1 Warning(s)";
        parser.eol(line.getBytes(StandardCharsets.UTF_8), line.length());

        assertEquals(1, parser.getNumberOfWarnings(), "Number of warnings should match");
    }

    @Test
    void testGetNumberOfErrors() throws Exception {
        String line = "    1 Error(s)";
        parser.eol(line.getBytes(StandardCharsets.UTF_8), line.length());

        assertEquals(1, parser.getNumberOfErrors(), "Number of errors should match");
    }

    @Test
    void testGetNumberOfWarningsAndErrors() throws Exception {
        String line1 = "    3 Warning(s)";
        String line2 = "    5 Error(s)";
        parser.eol(line1.getBytes(StandardCharsets.UTF_8), line1.length());
        parser.eol(line2.getBytes(StandardCharsets.UTF_8), line2.length());

        assertEquals(3, parser.getNumberOfWarnings(), "Number of warnings should match");
        assertEquals(5, parser.getNumberOfErrors(), "Number of errors should match");
    }

    @Test
    void testInvalidWarningCount() throws Exception {
        String line = "    Warnings: not a number";
        parser.eol(line.getBytes(StandardCharsets.UTF_8), line.length());

        assertEquals(-1, parser.getNumberOfWarnings(), "Number of warnings should be -1");
    }

    @Test
    void testInvalidErrorCount() throws Exception {
        String line = "    Errors: not a number";
        parser.eol(line.getBytes(StandardCharsets.UTF_8), line.length());

        assertEquals(-1, parser.getNumberOfErrors(), "Number of errors should be -1");
    }

    @Test
    void testClose() throws Exception {
        parser.close();
        verify(mockOutputStream, times(1)).close();
    }
}
