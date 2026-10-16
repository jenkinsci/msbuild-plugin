package hudson.plugins.msbuild;

import hudson.EnvVars;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolProperty;
import hudson.util.StreamTaskListener;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MsBuildInstallationTest {

    private final MsBuildInstallation msBuildInstallation =
            new MsBuildInstallation("Test", "/path/to/msbuild", Collections.emptyList(), "/p:Configuration=Release");

    @Test
    void defaultArgsAreTrimmed() {
        assertEquals("/p:Configuration=Release", msBuildInstallation.getDefaultArgs());
        assertEquals("/m", new MsBuildInstallation("T", "/x", Collections.emptyList(), "  /m  ").getDefaultArgs());
        assertNull(new MsBuildInstallation("T", "/x", Collections.emptyList(), "   ").getDefaultArgs());
    }

    @Test
    void buildEnvVars() {
        EnvVars envVars = new EnvVars();
        msBuildInstallation.buildEnvVars(envVars);

        assertEquals("/path/to/msbuild", envVars.get("MSBUILD_HOME"));
        assertEquals("/p:Configuration=Release", envVars.get("MSBUILD_ARGS"));
        assertEquals("/path/to/msbuild", envVars.get("PATH+MSBUILD"));
    }

    @Test
    void buildEnvVarsWithNullDefaultArgs() {
        EnvVars envVars = new EnvVars();
        new MsBuildInstallation("Test", "/path/to/msbuild", Collections.emptyList(), null).buildEnvVars(envVars);

        assertEquals("", envVars.get("MSBUILD_ARGS"));
    }

    @Test
    void forEnvironmentExpandsHome() {
        MsBuildInstallation templated =
                new MsBuildInstallation("Test", "${TOOLS}/msbuild", Collections.emptyList(), "/m");

        MsBuildInstallation expanded = templated.forEnvironment(new EnvVars("TOOLS", "/opt"));

        assertEquals("Test", expanded.getName());
        assertEquals("/opt/msbuild", expanded.getHome());
        assertEquals("/m", expanded.getDefaultArgs());
    }

    @Test
    @WithJenkins
    void forNodeKeepsHomeWithoutInstallers(JenkinsRule r) throws Exception {
        MsBuildInstallation onNode = msBuildInstallation.forNode(r.jenkins, StreamTaskListener.fromStdout());

        assertEquals("/path/to/msbuild", onNode.getHome());
        assertEquals("/p:Configuration=Release", onNode.getDefaultArgs());
    }

    @Test
    @WithJenkins
    void descriptorStoresInstallationsOnBuilderDescriptor(JenkinsRule r) {
        MsBuildInstallation.DescriptorImpl descriptor =
                r.jenkins.getDescriptorByType(MsBuildInstallation.DescriptorImpl.class);
        assertEquals("MSBuild", descriptor.getDisplayName());

        descriptor.setInstallations(msBuildInstallation);

        assertArrayEquals(new MsBuildInstallation[] {msBuildInstallation}, descriptor.getInstallations());
        assertArrayEquals(new MsBuildInstallation[] {msBuildInstallation},
                r.jenkins.getDescriptorByType(MsBuildBuilder.DescriptorImpl.class).getInstallations());
        List<? extends hudson.tools.ToolInstaller> installers = descriptor.getDefaultInstallers();
        assertEquals(1, installers.size());
        assertInstanceOf(MsBuildInstaller.class, installers.get(0));
    }

    @Test
    @WithJenkins
    void toolConfigRoundtrip(JenkinsRule r) throws Exception {
        MsBuildInstaller installer = new MsBuildInstaller("windows");
        installer.setSelectedVersion("2022");
        installer.setAdditionalArguments("--installPath D:\\BuildTools");
        installer.setVsconfig("{\"components\":[\"Microsoft.VisualStudio.Workload.MSBuildTools\"]}");
        List<ToolProperty<?>> props = List.of(new InstallSourceProperty(List.of(installer)));
        MsBuildInstallation before = new MsBuildInstallation("VS2022", "", props, "/m /nologo");
        MsBuildInstallation.DescriptorImpl descriptor =
                r.jenkins.getDescriptorByType(MsBuildInstallation.DescriptorImpl.class);
        descriptor.setInstallations(before);

        r.submit(r.createWebClient().goTo("manage/configureTools").getFormByName("config"));

        MsBuildInstallation[] after = descriptor.getInstallations();
        assertEquals(1, after.length);
        assertNotSame(before, after[0]);
        r.assertEqualDataBoundBeans(before, after[0]);
        r.assertEqualDataBoundBeans(installer,
                after[0].getProperties().get(InstallSourceProperty.class).installers.get(MsBuildInstaller.class));
    }
}
