package hudson.plugins.msbuild;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentSpecific;
import hudson.model.Hudson;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * @author Gregory Boissinot
 */
public final class MsBuildInstallation extends ToolInstallation implements NodeSpecific<MsBuildInstallation>, EnvironmentSpecific<MsBuildInstallation> {

    @SuppressWarnings("unused")
    /**
     * Backward compat
     */
    private transient String pathToMsBuild;
    private String defaultArgs;

    @DataBoundConstructor
    public MsBuildInstallation(String name, String home, String defaultArgs) {
        super(name, home, null);
        this.defaultArgs = defaultArgs;
    }

    public MsBuildInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new MsBuildInstallation(getName(), translateFor(node, log), getDefaultargs());
    }

    public MsBuildInstallation forEnvironment(EnvVars environment) {
        return new MsBuildInstallation(getName(), environment.expand(getHome()), getDefaultargs());
    }

    public String getDefaultargs() {
        return this.defaultArgs;
    }

    @Extension
    public static class DescriptorImpl extends ToolDescriptor<MsBuildInstallation> {

        public String getDisplayName() {
            return "MSBuild";
        }

        @Override
        public MsBuildInstallation[] getInstallations() {
            return Hudson.getInstance().getDescriptorByType(MsBuildBuilder.DescriptorImpl.class).getInstallations();
        }

        @Override
        public void setInstallations(MsBuildInstallation... installations) {
            Hudson.getInstance().getDescriptorByType(MsBuildBuilder.DescriptorImpl.class).setInstallations(installations);
        }

    }
}
