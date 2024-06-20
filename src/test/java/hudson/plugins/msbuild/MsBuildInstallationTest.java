package hudson.plugins.msbuild;

import hudson.EnvVars;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolProperty;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class MsBuildInstallationTest {

    @Mock
    private Node node;

    @Mock
    private TaskListener taskListener;

    private MsBuildInstallation msBuildInstallation;

    @Before
    public void setUp() {
        List<ToolProperty<?>> properties = Collections.emptyList();
        msBuildInstallation = new MsBuildInstallation("Test", "/path/to/msbuild", properties, "/p:Configuration=Release");
    }

    @Test
    public void testGetDefaultArgs() {
        assertEquals("/p:Configuration=Release", msBuildInstallation.getDefaultArgs());
    }

    @Test
    public void testBuildEnvVars() {
        EnvVars envVars = new EnvVars();
        msBuildInstallation.buildEnvVars(envVars);

        assertEquals("/path/to/msbuild", envVars.get("MSBUILD_HOME"));
        assertEquals("/p:Configuration=Release", envVars.get("MSBUILD_ARGS"));
        assertEquals("/path/to/msbuild", envVars.get("PATH+MSBUILD"));
    }

    @Test
    public void testForEnvironment() {
        EnvVars environment = new EnvVars();
        environment.put("HOME", "/path/to/msbuild");

        MsBuildInstallation updatedInstallation = msBuildInstallation.forEnvironment(environment);

        assertEquals(msBuildInstallation.getName(), updatedInstallation.getName());
        assertEquals("/path/to/msbuild", updatedInstallation.getHome());
        assertEquals(msBuildInstallation.getDefaultArgs(), updatedInstallation.getDefaultArgs());
    }
}