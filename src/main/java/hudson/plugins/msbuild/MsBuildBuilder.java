package hudson.plugins.msbuild;

import hudson.*;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * @author kyle.sweeney@valtech.com
 */
public class MsBuildBuilder extends Builder {

    /**
     * GUI fields
     */
    private final String msBuildName;
    private final String msBuildFile;
    private final String cmdLineArgs;

    /**
     * When this builder is created in the project configuration step,
     * the builder object will be created from the strings below.
     *
     * @param msBuildName The Visual studio logical name
     * @param msBuildFile The name/location of the msbuild file
     * @param cmdLineArgs Whitespace separated list of command line arguments
     */
    @DataBoundConstructor
    @SuppressWarnings("unused")
    public MsBuildBuilder(String msBuildName, String msBuildFile, String cmdLineArgs) {
        this.msBuildName = msBuildName;
        this.msBuildFile = msBuildFile;
        this.cmdLineArgs = cmdLineArgs;
    }

    @SuppressWarnings("unused")
    public String getCmdLineArgs() {
        return cmdLineArgs;
    }

    @SuppressWarnings("unused")
    public String getMsBuildFile() {
        return msBuildFile;
    }

    @SuppressWarnings("unused")
    public String getMsBuildName() {
        return msBuildName;
    }

    public MsBuildInstallation getMsBuild() {
        for (MsBuildInstallation i : DESCRIPTOR.getInstallations()) {
            if (msBuildName != null && i.getName().equals(msBuildName))
                return i;
        }
        return null;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        ArgumentListBuilder args = new ArgumentListBuilder();

        String execName = "msbuild.exe";
        MsBuildInstallation ai = getMsBuild();
        if (ai == null) {
            listener.getLogger().println("Path To MSBuild.exe: " + execName);
            args.add(execName);
        } else {
            EnvVars env = build.getEnvironment(listener);
            ai = ai.forNode(Computer.currentComputer().getNode(), listener);
            ai = ai.forEnvironment(env);
            String pathToMsBuild = ai.getHome();
            FilePath exec = new FilePath(launcher.getChannel(), pathToMsBuild);
            try {
                if (!exec.exists()) {
                    listener.fatalError(pathToMsBuild + " doesn't exist");
                    return false;
                }
            } catch (IOException e) {
                listener.fatalError("Failed checking for existence of " + pathToMsBuild);
                return false;
            }
            listener.getLogger().println("Path To MSBuild.exe: " + pathToMsBuild);
            args.add(pathToMsBuild);

            if (ai.getDefaultArgs() != null) {
                args.addTokenized(ai.getDefaultArgs());
            }
        }

        EnvVars env = build.getEnvironment(listener);
        String normalizedArgs = cmdLineArgs.replaceAll("[\t\r\n]+", " ");
        normalizedArgs = Util.replaceMacro(normalizedArgs, env);
        normalizedArgs = Util.replaceMacro(normalizedArgs, build.getBuildVariables());
        if (normalizedArgs.trim().length() > 0)
            args.addTokenized(normalizedArgs);

        args.addKeyValuePairs("/P:", build.getBuildVariables());

        //If a msbuild file is specified, then add it as an argument, otherwise
        //msbuild will search for any file that ends in .proj or .sln
        if (msBuildFile != null && msBuildFile.trim().length() > 0) {
            String normalizedFile = msBuildFile.replaceAll("[\t\r\n]+", " ");
            normalizedFile = Util.replaceMacro(normalizedFile, env);
            normalizedFile = Util.replaceMacro(normalizedFile, build.getBuildVariables());
            if (normalizedFile.length() > 0)
                args.add(normalizedFile);
        }

        if (!launcher.isUnix()) {
            args.prepend("cmd.exe", "/C");
            args.add("&&", "exit", "%%ERRORLEVEL%%");
        }

        listener.getLogger().println("Executing command: " + args.toStringWithQuote());
        try {
            int r = launcher.launch().cmds(args).envs(env).stdout(listener).pwd(build.getModuleRoot()).join();
            return r == 0;
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("command execution failed"));
            return false;
        }
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        return DESCRIPTOR;
    }

    /**
     * Descriptor should be singleton.
     */
    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    public static final class DescriptorImpl extends Descriptor<Builder> {

        @CopyOnWrite
        private volatile MsBuildInstallation[] installations = new MsBuildInstallation[0];

        DescriptorImpl() {
            super(MsBuildBuilder.class);
            load();
        }

        public String getDisplayName() {
            return "Build a Visual Studio project or solution using MSBuild.";
        }


        public MsBuildInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(MsBuildInstallation... antInstallations) {
            this.installations = antInstallations;
            save();
        }

        /**
         * Obtains the {@link MsBuildInstallation.DescriptorImpl} instance.
         */
        public MsBuildInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(MsBuildInstallation.DescriptorImpl.class);
        }

    }

}
