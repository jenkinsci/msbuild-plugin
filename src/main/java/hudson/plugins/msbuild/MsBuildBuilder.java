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
import java.util.Map;

/**
 * Sample {@link Builder}.
 * <p/>
 * <p/>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link MsBuildBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 * <p/>
 * <p/>
 * When a build is performed, the {@link #perform(Build, Launcher, BuildListener)} method
 * will be invoked.
 *
 * @author kyle.sweeney@valtech.com
 *         2009/03/01 -- Gregory Boissinot - Zenika - Add the possibility to manage multiple Msbuild version
 *         2009/05/20 - Fix #3610
 */
public class MsBuildBuilder extends Builder {

    /**
     * Identifies {@link Visual Studio} to be used.
     */
    private final String msBuildName;
    private final String msBuildFile;
    private final String cmdLineArgs;

    /**
     * When this builder is created in the project configuration step,
     * the builder object will be created from the strings below.
     *
     * @param msName      The Visual studio logical identifiant name
     * @param msBuildFile The name/location of the msbuild file
     * @param targets     Whitespace separated list of command line arguments
     */
    @DataBoundConstructor
    public MsBuildBuilder(String msBuildName, String msBuildFile, String cmdLineArgs) {
        this.msBuildName = msBuildName;
        this.msBuildFile = msBuildFile;
        this.cmdLineArgs = cmdLineArgs;
    }

    public String getCmdLineArgs() {
        return cmdLineArgs;
    }

    public String getMsBuildFile() {
        return msBuildFile;
    }

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
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
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

        //Remove all tabs, carriage returns, and newlines and replace them with
        //whitespaces, so that we can add them as parameters to the executable
        String normalizedTarget = cmdLineArgs.replaceAll("[\t\r\n]+", " ");
        if (normalizedTarget.trim().length() > 0)
            args.addTokenized(normalizedTarget);

        args.addKeyValuePairs("-P:", build.getBuildVariables());

        //If a msbuild file is specified, then add it as an argument, otherwise
        //msbuild will search for any file that ends in .proj or .sln
        if (msBuildFile != null && msBuildFile.trim().length() > 0) {
            args.add(msBuildFile);
        }

        //According to the Ant builder source code, in order to launch a program 
        //from the command line in windows, we must wrap it into cmd.exe.  This 
        //way the return code can be used to determine whether or not the build failed.
        if (!launcher.isUnix()) {
            args.prepend("cmd.exe", "/C");
            args.add("&&", "exit", "%%ERRORLEVEL%%");
        }

        //Try to execute the command
        listener.getLogger().println("Executing command: " + args.toStringWithQuote());
        try {
            Map<String, String> env = build.getEnvironment(listener);
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
