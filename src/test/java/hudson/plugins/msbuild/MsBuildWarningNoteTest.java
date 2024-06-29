package hudson.plugins.msbuild;

import hudson.MarkupText;
import org.junit.jupiter.api.Test;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

public class MsBuildWarningNoteTest {

    @Test
    public void testPatternMatchesWarningMessage() {
        String warningMessage = "SomeFile.cs(123,45): warning CS1234: Some warning message";
        Matcher matcher = MSBuildWarningNote.PATTERN.matcher(warningMessage);
        assertTrue(matcher.matches(), "Pattern should match the warning message format");

        assertEquals("SomeFile.cs", matcher.group(1), "Filename should match");
        assertEquals(",45", matcher.group(2), "Column number should match");
        assertEquals("CS1234", matcher.group(3), "Warning code should match");
        assertEquals("CS", matcher.group(4), "Warning code prefix should match");
        assertEquals("Some warning message", matcher.group(5), "Warning message should match");
    }

    @Test
    public void testAnnotateAddsMarkup() {
        String warningMessage = "SomeFile.cs(123,45): warning CS1234: Some warning message";
        MarkupText markupText = new MarkupText(warningMessage);
        MSBuildWarningNote note = new MSBuildWarningNote();

        note.annotate(null, markupText, 0);

        assertEquals("<span class=warning-inline>" + warningMessage + "</span>",
                markupText.toString(true),
                "The markup should be correctly applied");
    }

    @Test
    public void testDescriptorDisplayName() {
        MSBuildWarningNote.DescriptorImpl descriptor = new MSBuildWarningNote.DescriptorImpl();
        assertEquals("MSBuild warning", descriptor.getDisplayName(), "Display name should be 'MSBuild warning'");
    }
}
