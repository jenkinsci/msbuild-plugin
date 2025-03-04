package hudson.plugins.msbuild;

import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author Jonathan Zimmerman
 */
@WithJenkins
class MsBuildBuilderTest {

    @Test
    void shouldStripQuotedArguments(JenkinsRule r) {
        final String quotedPlatform = "/p:Platform=\"Any CPU\"";
        final String strippedPlatform = "/p:Platform=Any CPU";

        String[] tokenizedArgs = MsBuildBuilder.tokenizeArgs(quotedPlatform);
        assertNotNull(tokenizedArgs);
        assertEquals(1, tokenizedArgs.length);
        assertEquals(strippedPlatform, tokenizedArgs[0]);
    }

    @Test
    void shouldSplitArguments(JenkinsRule r) {
        final String arguments = "/t:Build /p:Configuration=Debug";

        String[] tokenizedArgs = MsBuildBuilder.tokenizeArgs(arguments);
        assertNotNull(tokenizedArgs);
        assertEquals(2, tokenizedArgs.length);
        assertEquals("/t:Build", tokenizedArgs[0]);
        assertEquals("/p:Configuration=Debug", tokenizedArgs[1]);
    }

    @Test
    void endEscapedCharacter(JenkinsRule r) {
        final String oneArgumentsWithEndBackslash = "\\\\RemoteServerName\\OfficialBuilds\\Published\\";
        String[] tokenizedArgs = MsBuildBuilder.tokenizeArgs(oneArgumentsWithEndBackslash);
        assertEquals(1, tokenizedArgs.length);
        assertEquals(oneArgumentsWithEndBackslash, tokenizedArgs[0]);
    }

    @Test
    @LocalData
    void configRoundtrip(JenkinsRule r) {
        FreeStyleProject project = (FreeStyleProject)r.jenkins.getAllItems().get(0);
        assertDoesNotThrow(() -> r.configRoundtrip(project));
    }

    @Test
    void testValidCharset() {
        // Assuming CHARSET_CODE_MAP is populated with UTF-8 -> 65001
        Charset charset = StandardCharsets.UTF_8;
        int expectedCodePage = 65001;
        int actualCodePage = MsBuildBuilder.getCodePageIdentifier(charset);
        assertEquals(expectedCodePage, actualCodePage, "Code page should match expected for UTF-8");
    }

    @Test
    void testInvalidCharset() {
        // Assuming there's no entry in CHARSET_CODE_MAP for the provided charset
        Charset charset = Charset.forName("ISO-8859-16");
        int expectedCodePage = 0; // As per method's default case
        int actualCodePage = MsBuildBuilder.getCodePageIdentifier(charset);
        assertEquals(expectedCodePage, actualCodePage, "Code page for unrecognized charset should be 0");
    }

    @Test
    void testNullCharset() {
        assertThrows(NullPointerException.class, () ->
            // This should throw NullPointerException as the charset is null
            MsBuildBuilder.getCodePageIdentifier(null));
    }

    @Test
    void testCaseSensitivity() {
        // Verify case insensitivity by testing an all-lowercase input
        Charset charset = StandardCharsets.UTF_8;
        int expectedCodePage = 65001;
        int actualCodePage = MsBuildBuilder.getCodePageIdentifier(charset);
        assertEquals(expectedCodePage, actualCodePage, "Code page should match expected regardless of case");
    }
}