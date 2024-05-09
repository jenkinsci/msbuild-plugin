package hudson.plugins.msbuild;

import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import java.util.regex.Pattern;

/**
 * Annotation for MSBuild warning messages
 */
public class MSBuildWarningNote extends ConsoleNote<Object> {
    /** Pattern to identify warning messages */

    private static final long serialVersionUID = 1L;

    public final static Pattern PATTERN = Pattern.compile("(.*)\\(\\d+(,\\d+){0,1}\\):\\s[Ww]arning\\s(([A-Z]*)\\d+){0,1}:\\s(.*)");
    
    public MSBuildWarningNote() {
    }

    @Override
    public ConsoleAnnotator<Object> annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(0, text.length(), "<span class=warning-inline>", "</span>");
        return null;
    }

    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.MsBuildBuilder_WarningNoteDescription();
        }
    }
}
