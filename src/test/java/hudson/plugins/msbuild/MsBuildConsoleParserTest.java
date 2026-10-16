package hudson.plugins.msbuild;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MsBuildConsoleParserTest {

    private ByteArrayOutputStream out;
    private MsBuildConsoleParser parser;

    @BeforeEach
    void setUp() {
        out = new ByteArrayOutputStream();
        parser = new MsBuildConsoleParser(out, StandardCharsets.UTF_8);
    }

    private void feed(String... lines) throws IOException {
        for (String line : lines) {
            parser.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        parser.flush();
    }

    @Test
    void countsAreMinusOneUntilSummaryIsSeen() throws IOException {
        feed("Build started.", "Project \"a.sln\" on node 1 (default targets).");

        assertEquals(-1, parser.getNumberOfWarnings());
        assertEquals(-1, parser.getNumberOfErrors());
    }

    @Test
    void parsesMsBuildSummary() throws IOException {
        feed("Build succeeded.", "    3 Warning(s)", "    5 Error(s)");

        assertEquals(3, parser.getNumberOfWarnings());
        assertEquals(5, parser.getNumberOfErrors());
    }

    @Test
    void parsesSummaryRegardlessOfIndentation() throws IOException {
        feed("1 Warning(s)", "\t\t12 Error(s)");

        assertEquals(1, parser.getNumberOfWarnings());
        assertEquals(12, parser.getNumberOfErrors());
    }

    @Test
    void parsesCountEmbeddedInOtherText() throws IOException {
        // used to throw ArrayIndexOutOfBoundsException
        feed("Build succeeded. 7 Warning(s)", "Done: 2 Error(s) found");

        assertEquals(7, parser.getNumberOfWarnings());
        assertEquals(2, parser.getNumberOfErrors());
    }

    @Test
    void lastSummaryWins() throws IOException {
        feed("    4 Warning(s)", "    0 Warning(s)");

        assertEquals(0, parser.getNumberOfWarnings());
    }

    @Test
    void countTooLargeForIntIsIgnored() throws IOException {
        feed("    2 Warning(s)", "    99999999999 Warning(s)", "    99999999999 Error(s)");

        assertEquals(2, parser.getNumberOfWarnings());
        assertEquals(-1, parser.getNumberOfErrors());
    }

    @Test
    void passesOutputThroughUnchanged() throws IOException {
        String log = "line one\r\n    1 Warning(s)\nline three\n";
        parser.write(log.getBytes(StandardCharsets.UTF_8));
        parser.flush();

        assertEquals(log, out.toString(StandardCharsets.UTF_8));
    }

    @Test
    void closeClosesWrappedStream() throws IOException {
        OutputStream wrapped = mock(OutputStream.class);
        new MsBuildConsoleParser(wrapped, StandardCharsets.UTF_8).close();

        verify(wrapped).close();
    }
}
