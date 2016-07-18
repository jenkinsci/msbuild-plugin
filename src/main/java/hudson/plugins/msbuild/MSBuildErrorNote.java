package hudson.plugins.msbuild;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import java.util.regex.Pattern;

/**
 * Annotation for MSBuild and CSC error messages
 */
public class MSBuildErrorNote extends ConsoleNote {
    /** Pattern to identify error messages */
    public final static Pattern PATTERN = Pattern.compile("(.*)[Ee]rror\\s(([A-Z]*)\\d+){0,1}:\\s(.*)");
    
    public MSBuildErrorNote() {
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(0, text.length(), "<span class=error-inline>", "</span>");
        return null;
    }

    @Extension
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {

        public String getDisplayName() {
            return Messages.MsBuildBuilder_ErrorNoteDescription();
        }
    }
}
