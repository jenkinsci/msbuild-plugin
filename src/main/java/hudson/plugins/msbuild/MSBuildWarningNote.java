package hudson.plugins.msbuild;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import java.util.regex.Pattern;

/**
 * Annotation for MSBuild warning messages
 */
public class MSBuildWarningNote extends ConsoleNote {
    /** Pattern to identify doxygen warning message */
    public static Pattern PATTERN = Pattern.compile("(.*)\\(\\d+,\\d+\\):\\swarning\\s:\\s(.*)");
    
    public MSBuildWarningNote() {
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(0, text.length(), "<span class=warning-inline>", "</span>");
        return null;
    }

    @Extension
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {

        public String getDisplayName() {
            return Messages.MsBuildBuilder_WarningNoteDescription();
        }
    }
}