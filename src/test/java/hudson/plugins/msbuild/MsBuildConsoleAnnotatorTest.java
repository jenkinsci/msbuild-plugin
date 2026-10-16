package hudson.plugins.msbuild;

import hudson.console.ConsoleNote;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MsBuildConsoleAnnotatorTest {

    private static final String WARNING = "C:\\src\\Foo.cs(10,20): warning CS0168: The variable 'x' is declared but never used";
    private static final String ERROR = "C:\\src\\Foo.cs(10,20): error CS1002: ; expected";

    private ByteArrayOutputStream out;
    private MSBuildConsoleAnnotator annotator;

    @BeforeEach
    void setUp() {
        out = new ByteArrayOutputStream();
        annotator = new MSBuildConsoleAnnotator(out, StandardCharsets.UTF_8);
    }

    private String feed(String... lines) throws IOException {
        for (String line : lines) {
            annotator.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        annotator.flush();
        return out.toString(StandardCharsets.UTF_8);
    }

    @Test
    void annotatesWarning() throws IOException {
        String log = feed(WARNING);

        assertEquals(1, annotator.getNumberOfWarnings());
        assertEquals(0, annotator.getNumberOfErrors());
        assertTrue(log.startsWith(ConsoleNote.PREAMBLE_STR), "note must precede the line");
        assertTrue(log.endsWith(WARNING + "\n"));
    }

    @Test
    void annotatesError() throws IOException {
        String log = feed(ERROR);

        assertEquals(0, annotator.getNumberOfWarnings());
        assertEquals(1, annotator.getNumberOfErrors());
        assertTrue(log.startsWith(ConsoleNote.PREAMBLE_STR), "note must precede the line");
        assertTrue(log.endsWith(ERROR + "\n"));
    }

    @Test
    void countsEveryMatchingLine() throws IOException {
        feed(WARNING, ERROR, WARNING, "plain line");

        assertEquals(2, annotator.getNumberOfWarnings());
        assertEquals(1, annotator.getNumberOfErrors());
    }

    @Test
    void leavesOtherLinesUntouched() throws IOException {
        String line = "Build succeeded. Ünïcode ✓";
        String log = feed(line);

        assertEquals(0, annotator.getNumberOfWarnings());
        assertEquals(0, annotator.getNumberOfErrors());
        assertFalse(log.contains(ConsoleNote.PREAMBLE_STR));
        assertEquals(line + "\n", log);
    }

    @Test
    void errorPatternMatchesMsBuildFormats() {
        for (String line : new String[] {ERROR, "  1>CSC : error CS2001: Source file 'x.cs' could not be found",
                "MSBUILD : error MSB1009: Project file does not exist.", "EXEC : error : boom",
                "C:\\src\\a.cpp(3): fatal error C1083: Cannot open include file", "error MSB4019: not found"}) {
            assertTrue(MSBuildErrorNote.PATTERN.matcher(line).matches(), line);
        }
    }

    @Test
    void errorPatternIgnoresLookalikes() {
        for (String line : new String[] {"Terror : x", "    0 Error(s)",
                "C:\\src\\Foo.cs(1,1): warning CS0618: see error CS1002: here"}) {
            assertFalse(MSBuildErrorNote.PATTERN.matcher(line).matches(), line);
        }
    }

    @Test
    void closeClosesWrappedStream() throws IOException {
        OutputStream wrapped = mock(OutputStream.class);
        new MSBuildConsoleAnnotator(wrapped, StandardCharsets.UTF_8).close();

        verify(wrapped).close();
    }
}
