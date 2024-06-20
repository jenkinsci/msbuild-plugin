package hudson.plugins.msbuild;

import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.Node;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.io.IOException;

@RunWith(MockitoJUnitRunner.class)
public class MsBuildInstallerTest {

    private MsBuildInstaller installer;

    @Mock
    private Node mockNode;
    @Mock
    private Computer mockComputer;
    @Mock
    private EnvVars mockEnvVars;

    @Before
    public void setUp() {
        installer = new MsBuildInstaller("testLabel");
    }

    @Test
    public void testSetAndGetSelectedVersion() {
        installer.setSelectedVersion("2022");
        assertEquals("2022", installer.getSelectedVersion());
    }

    @Test
    public void testSetAndGetAdditionalArguments() {
        installer.setAdditionalArguments("--quiet --norestart");
        assertEquals("--quiet --norestart", installer.getAdditionalArguments());
    }

    @Test
    public void testSetAndGetVsconfig() {
        installer.setVsconfig("{ \"version\": \"16.0\" }");
        assertEquals("{ \"version\": \"16.0\" }", installer.getVsconfig());
    }

    @Test
    public void testEnsureArguments() {
        String initialArgs = "--quiet";
        String[] argsToAdd = new String[] {"--wait", "--norestart"};
        String expected = "--quiet --wait --norestart";
        assertEquals(expected, MsBuildInstaller.ensureArguments(initialArgs, argsToAdd));
    }

    @Test
    public void testCheckIfOsIsWindows() throws IOException, InterruptedException {
        when(mockNode.toComputer()).thenReturn(mockComputer);
        when(mockComputer.getEnvironment()).thenReturn(mockEnvVars);
        when(mockEnvVars.containsKey("OS")).thenReturn(true);
        when(mockEnvVars.get("OS")).thenReturn("Windows_NT");

        assertTrue(MsBuildInstaller.checkIfOsIsWindows(mockNode));

        when(mockEnvVars.get("OS")).thenReturn("Linux");
        assertFalse(MsBuildInstaller.checkIfOsIsWindows(mockNode));
    }

    @Test
    public void testGetUrlForVersion() {
        assertEquals("https://aka.ms/vs/17/release/vs_buildtools.exe", MsBuildInstaller.DescriptorImpl.getUrlForVersion("2022"));
        assertEquals("https://aka.ms/vs/16/release/vs_buildtools.exe", MsBuildInstaller.DescriptorImpl.getUrlForVersion("2019"));
    }
}
