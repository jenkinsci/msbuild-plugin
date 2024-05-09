package hudson.plugins.msbuild;

import hudson.EnvVars;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class MsBuildInstallationTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    private MsBuildInstallation msBuildInstallation;

    @Before
    public void setUp() throws Exception {
        msBuildInstallation = new MsBuildInstallation("msbuild", "C:\\Program Files (x86)\\Microsoft Visual Studio\\bin", Collections.emptyList(), "");
        jenkinsRule.jenkins.getDescriptorByType(MsBuildInstallation.DescriptorImpl.class).setInstallations(msBuildInstallation);
    }

    @Test
    public void testBuildEnvVars() {
        EnvVars envVars = new EnvVars();
        msBuildInstallation.buildEnvVars(envVars);

        assertEquals("C:\\Program Files (x86)\\Microsoft Visual Studio\\bin", envVars.get("PATH+MSBUILD"));
    }

    @Test
    public void testForNode() throws IOException, InterruptedException {
        try {
            Node node = jenkinsRule.createSlave();
            MsBuildInstallation newInstallation = msBuildInstallation.forNode(node, TaskListener.NULL);

            assertEquals(msBuildInstallation.getName(), newInstallation.getName());
            assertEquals(msBuildInstallation.getHome(), newInstallation.getHome());
        } catch (Exception e) {
            throw new AssertionError("Not valid configuration for MsBuild", e);}
    }

    @Test
    public void testForEnvironment() {
        EnvVars environment = new EnvVars();
        environment.put("HOME", "C:\\Program Files (x86)\\Microsoft Visual Studio\\bin");

        MsBuildInstallation newInstallation = msBuildInstallation.forEnvironment(environment);

        assertEquals(msBuildInstallation.getName(), newInstallation.getName());
        assertEquals("C:\\Program Files (x86)\\Microsoft Visual Studio\\bin", newInstallation.getHome());
    }

    @TestExtension
    public static class TestToolInstallation extends ToolInstallation {

        public TestToolInstallation() {
            super("", "", Collections.emptyList());
        }
    }
}