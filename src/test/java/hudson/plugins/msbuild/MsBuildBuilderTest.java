package hudson.plugins.msbuild;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.nio.charset.Charset;

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

    @Test
    public void testValidCharset() {
        // Assuming CHARSET_CODE_MAP is populated with UTF-8 -> 65001
        Charset charset = Charset.forName("UTF-8");
        int expectedCodePage = 65001;
        int actualCodePage = MsBuildBuilder.getCodePageIdentifier(charset);
        assertEquals("Code page should match expected for UTF-8", expectedCodePage, actualCodePage);
    }

    @Test
    public void testInvalidCharset() {
        // Assuming there's no entry in CHARSET_CODE_MAP for the provided charset
        Charset charset = Charset.forName("ISO-8859-16");
        int expectedCodePage = 0; // As per method's default case
        int actualCodePage = MsBuildBuilder.getCodePageIdentifier(charset);
        assertEquals("Code page for unrecognized charset should be 0", expectedCodePage, actualCodePage);
    }

    @Test(expected = NullPointerException.class)
    public void testNullCharset() {
        // This should throw NullPointerException as the charset is null
        MsBuildBuilder.getCodePageIdentifier(null);
    }

    @Test
    public void testCaseSensitivity() {
        // Verify case insensitivity by testing an all-lowercase input
        Charset charset = Charset.forName("utf-8");
        int expectedCodePage = 65001;
        int actualCodePage = MsBuildBuilder.getCodePageIdentifier(charset);
        assertEquals("Code page should match expected regardless of case", expectedCodePage, actualCodePage);
    }
}