package hudson.plugins.msbuild;

import hudson.*;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;

/**
 * @author kyle.sweeney@valtech.com
 * @author Gregory Boissinot - Zenika
 *         2009/03/01 - Added the possibility to manage multiple Msbuild version
 *         2009/05/20 - Fixed #3610
 *         2010/04/02 - Fixed #4121
 *         2010/05/05 - Added environment and build variables resolving in fields
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
            String pathToMsBuild = ai.getPathToMsBuild();
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
        }

        EnvVars env = build.getEnvironment(listener);
        String normalizedArgs = cmdLineArgs.replaceAll("[\t\r\n]+", " ");
        normalizedArgs = Util.replaceMacro(normalizedArgs, env);
        normalizedArgs = Util.replaceMacro(normalizedArgs, build.getBuildVariables());
        if (normalizedArgs.trim().length() > 0)
            args.addTokenized(normalizedArgs);

        args.addKeyValuePairs("-P:", build.getBuildVariables());

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

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            installations = req.bindParametersToList(MsBuildInstallation.class, "msbuild.").toArray(new MsBuildInstallation[0]);
            save();
            return true;
        }

        public MsBuildInstallation[] getInstallations() {
            return installations;
        }

    }
}
