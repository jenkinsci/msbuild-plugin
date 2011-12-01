package hudson.plugins.msbuild;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
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
     * Backward compatibility
     */
    private transient String pathToMsBuild;

    private String defaultArgs;

    @DataBoundConstructor
    public MsBuildInstallation(String name, String home, String defaultArgs) {
        super(name, home, null);
        this.defaultArgs = Util.fixEmpty(defaultArgs);
    }

    public MsBuildInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new MsBuildInstallation(getName(), translateFor(node, log), getDefaultArgs());
    }

    public MsBuildInstallation forEnvironment(EnvVars environment) {
        return new MsBuildInstallation(getName(), environment.expand(getHome()), getDefaultArgs());
    }

    public String getDefaultArgs() {
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

    /**
     * Used for backward compatibility
     *
     * @return the new object, an instance of MsBuildInstallation
     */
    protected Object readResolve() {
        if (this.pathToMsBuild != null) {
            return new MsBuildInstallation(this.getName(), this.pathToMsBuild, this.defaultArgs);
        }
        return this;
    }
}
