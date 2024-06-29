package hudson.plugins.msbuild;

import hudson.MarkupText;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

public class MSBuildErrorNoteTest {

    @Test
    public void testPatternMatchesErrorMessage() {
        String errorMessage = "SomeFile.cs(123,45): error CS1234: Some error message";
        Matcher matcher = MSBuildErrorNote.PATTERN.matcher(errorMessage);
        assertTrue(matcher.matches(), "Pattern should match the error message format");

        assertEquals("SomeFile.cs(123,45):", matcher.group(1).trim(), "Filename and position should match");
        assertEquals("CS1234", matcher.group(2), "Error code should match");
        assertEquals("CS", matcher.group(3), "Error code prefix should match");
        assertEquals("Some error message", matcher.group(4), "Error message should match");
    }

    @Test
    public void testAnnotateAddsMarkup() {
        String errorMessage = "SomeFile.cs(123,45): error CS1234: Some error message";
        MarkupText markupText = new MarkupText(errorMessage);
        MSBuildErrorNote note = new MSBuildErrorNote();

        note.annotate(null, markupText, 0);

        assertEquals("<span class=error-inline>" + errorMessage + "</span>",
                markupText.toString(true),
                "The markup should be correctly applied");
    }

    @Test
    public void testDescriptorDisplayName() {
        MSBuildErrorNote.DescriptorImpl descriptor = new MSBuildErrorNote.DescriptorImpl();
        assertEquals("MSBuild error", descriptor.getDisplayName(), "Display name should be 'MSBuild error'");
    }
}
