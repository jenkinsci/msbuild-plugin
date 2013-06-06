package hudson.plugins.msbuild;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Jonathan Zimmerman
 */
public class MsBuildBuilderTest {
    
    @Test
    public void shouldRetainQuotedArguments() {
        final String platform = "/p:Platform=\"Any CPU\"";
        
        String[] tokenizedArgs = MsBuildBuilder.tokenizeArgs(platform);
        assertNotNull(tokenizedArgs);
        assertEquals(1, tokenizedArgs.length);
        assertEquals(platform, tokenizedArgs[0]);
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
}
