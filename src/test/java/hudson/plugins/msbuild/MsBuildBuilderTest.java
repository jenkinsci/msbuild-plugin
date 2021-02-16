package hudson.plugins.msbuild;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Jonathan Zimmerman
 */
public class MsBuildBuilderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void shouldStripQuotedArguments() {
        final String quotedPlatform = "/p:Platform=\"Any CPU\"";
        final String strippedPlatform = "/p:Platform=Any CPU";

        String[] tokenizedArgs = MsBuildBuilder.tokenizeArgs(quotedPlatform);
        assertNotNull(tokenizedArgs);
        assertEquals(1, tokenizedArgs.length);
        assertEquals(strippedPlatform, tokenizedArgs[0]);
    }

    @Test
    public void shouldSplitArguments() {
        final String arguments = "/t:Build /p:Configuration=Debug";

        String[] tokenizedArgs = MsBuildBuilder.tokenizeArgs(arguments);
        assertNotNull(tokenizedArgs);
        assertEquals(2, tokenizedArgs.length);
        assertEquals("/t:Build", tokenizedArgs[0]);
        assertEquals("/p:Configuration=Debug", tokenizedArgs[1]);
    }

    @Test
    public void endEscapedCharacter() {
        final String oneArgumentsWithEndBackslash = "\\\\RemoteServerName\\OfficialBuilds\\Published\\";
        String[] tokenizedArgs = MsBuildBuilder.tokenizeArgs(oneArgumentsWithEndBackslash);
        assertEquals(1, tokenizedArgs.length);
        assertEquals(oneArgumentsWithEndBackslash, tokenizedArgs[0]);
    }

    @Test
    @LocalData
    public void configRoundtrip() {
        try {
            FreeStyleProject project = (FreeStyleProject)r.jenkins.getAllItems().get(0);
            r.configRoundtrip(project);
        } catch (Exception e) {
            throw new AssertionError("Not valid configuration for MsBuild", e);
        }
    }

}
