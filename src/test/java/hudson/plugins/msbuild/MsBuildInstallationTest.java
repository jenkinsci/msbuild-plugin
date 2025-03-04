package hudson.plugins.msbuild;

import hudson.EnvVars;
import hudson.tools.ToolProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MsBuildInstallationTest {

    private MsBuildInstallation msBuildInstallation;

    @BeforeEach
    void setUp() {
        List<ToolProperty<?>> properties = Collections.emptyList();
        msBuildInstallation = new MsBuildInstallation("Test", "/path/to/msbuild", properties, "/p:Configuration=Release");
    }

    @Test
    void testGetDefaultArgs() {
        assertEquals("/p:Configuration=Release", msBuildInstallation.getDefaultArgs());
    }

    @Test
    void testBuildEnvVars() {
        EnvVars envVars = new EnvVars();
        msBuildInstallation.buildEnvVars(envVars);

        assertEquals("/path/to/msbuild", envVars.get("MSBUILD_HOME"));
        assertEquals("/p:Configuration=Release", envVars.get("MSBUILD_ARGS"));
        assertEquals("/path/to/msbuild", envVars.get("PATH+MSBUILD"));
    }

    @Test
    void testBuildEnvVarsWithNullDefaultArgs() {
        List<ToolProperty<?>> properties = Collections.emptyList();
        MsBuildInstallation msBuildInstallationWithNullArgs = new MsBuildInstallation("Test", "/path/to/msbuild", properties, null);
        EnvVars envVars = new EnvVars();
        msBuildInstallationWithNullArgs.buildEnvVars(envVars);

        assertEquals("/path/to/msbuild", envVars.get("MSBUILD_HOME"));
        assertEquals("", envVars.get("MSBUILD_ARGS")); // Ensuring that MSBUILD_ARGS is set to an empty string when defaultArgs is null
        assertEquals("/path/to/msbuild", envVars.get("PATH+MSBUILD"));
    }

    @Test
    void testForEnvironment() {
        EnvVars environment = new EnvVars();
        environment.put("HOME", "/path/to/msbuild");

        MsBuildInstallation updatedInstallation = msBuildInstallation.forEnvironment(environment);

        assertEquals(msBuildInstallation.getName(), updatedInstallation.getName());
        assertEquals("/path/to/msbuild", updatedInstallation.getHome());
        assertEquals(msBuildInstallation.getDefaultArgs(), updatedInstallation.getDefaultArgs());
    }

    @Test
    void testGetDisplayName() {
        MsBuildInstallation.DescriptorImpl descriptor = new MsBuildInstallation.DescriptorImpl();
        assertEquals("MSBuild", descriptor.getDisplayName());
    }
}