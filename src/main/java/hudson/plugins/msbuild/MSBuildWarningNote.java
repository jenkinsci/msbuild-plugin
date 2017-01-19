package hudson.plugins.msbuild;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import org.jenkinsci.Symbol;

import java.util.regex.Pattern;

/**
 * Annotation for MSBuild warning messages
 */
public class MSBuildWarningNote extends ConsoleNote {
    /** Pattern to identify warning messages */
    public final static Pattern PATTERN = Pattern.compile("(.*)\\(\\d+(,\\d+){0,1}\\):\\s[Ww]arning\\s(([A-Z]*)\\d+){0,1}:\\s(.*)");
    
    public MSBuildWarningNote() {
    }

    @Override
    public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
        text.addMarkup(0, text.length(), "<span class=warning-inline>", "</span>");
        return null;
    }

    @Extension @Symbol("msbuildWarning")
    public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {

        @Override
        public String getDisplayName() {
            return Messages.MsBuildBuilder_WarningNoteDescription();
        }
    }
}
