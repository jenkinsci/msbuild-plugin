package hudson.plugins.msbuild;

import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import hudson.util.QuotedStringTokenizer;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

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
    private final boolean buildVariablesAsProperties;
    private transient boolean continueOnBuilFailure;
    private final boolean continueOnBuildFailure;
    private final boolean unstableIfWarnings;

    /**
     * When this builder is created in the project configuration step,
     * the builder object will be created from the strings below.
     *
     * @param msBuildName                The Visual Studio logical name
     * @param msBuildFile                The name/location of the MSBuild file
     * @param cmdLineArgs                Whitespace separated list of command line arguments
     * @param buildVariablesAsProperties If true, pass build variables as properties to MSBuild
     * @param continueOnBuildFailure     If true, job will continue dispite of MSBuild build failure
     * @param unstableIfWarnings         If true, job will be unstable if there are warnings
     */
    @DataBoundConstructor
    @SuppressWarnings("unused")
    public MsBuildBuilder(String msBuildName, String msBuildFile, String cmdLineArgs, boolean buildVariablesAsProperties, boolean continueOnBuildFailure, boolean unstableIfWarnings) {
        this.msBuildName = msBuildName;
        this.msBuildFile = msBuildFile;
        this.cmdLineArgs = cmdLineArgs;
        this.buildVariablesAsProperties = buildVariablesAsProperties;
        this.continueOnBuildFailure = continueOnBuildFailure;
        this.unstableIfWarnings = unstableIfWarnings;
    }

    @SuppressWarnings("unused")
    public String getMsBuildFile() {
        return msBuildFile;
    }

    @SuppressWarnings("unused")
    public String getMsBuildName() {
        return msBuildName;
    }

    @SuppressWarnings("unused")
    public String getCmdLineArgs() {
        return cmdLineArgs;
    }

    @SuppressWarnings("unused")
    public boolean getBuildVariablesAsProperties() {
        return buildVariablesAsProperties;
    }

    @SuppressWarnings("unused")
    public boolean getContinueOnBuildFailure() {
        return continueOnBuildFailure;
    }

    @SuppressWarnings("unused")
    public boolean getUnstableIfWarnings() {
        return unstableIfWarnings;
    }

    public MsBuildInstallation getMsBuild() {
        DescriptorImpl descriptor = (DescriptorImpl)getDescriptor();
        for (MsBuildInstallation i : descriptor.getInstallations()) {
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
                args.add(tokenizeArgs(ai.getDefaultArgs()));
            }
        }

        EnvVars env = build.getEnvironment(listener);
        String normalizedArgs = cmdLineArgs.replaceAll("[\t\r\n]+", " ");
        normalizedArgs = Util.replaceMacro(normalizedArgs, env);
        normalizedArgs = Util.replaceMacro(normalizedArgs, build.getBuildVariables());

        if (normalizedArgs.trim().length() > 0)
            args.add(tokenizeArgs(normalizedArgs));

        //Build /P:key1=value1;key2=value2 ...
        Map<String, String> variables = build.getBuildVariables();

        if (buildVariablesAsProperties && variables.size() != 0) {
            StringBuffer parameters = new StringBuffer();
            parameters.append("/p:");
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String value = URLEncoder.encode(entry.getValue(), "UTF-8");
                parameters.append(entry.getKey()).append("=").append(value).append(";");
            }
            parameters.delete(parameters.length() - 1, parameters.length());
            args.add(parameters.toString());
        }

        //If a msbuild file is specified, then add it as an argument, otherwise
        //msbuild will search for any file that ends in .proj or .sln
        String normalizedFile = null;
        if (msBuildFile != null && msBuildFile.trim().length() != 0) {
            normalizedFile = msBuildFile.replaceAll("[\t\r\n]+", " ");
            normalizedFile = Util.replaceMacro(normalizedFile, env);
            normalizedFile = Util.replaceMacro(normalizedFile, build.getBuildVariables());
            if (normalizedFile.length() > 0) {
                args.add(normalizedFile);
            }
        }

        FilePath pwd = build.getModuleRoot();
        if (normalizedFile != null) {
            FilePath msBuildFilePath = pwd.child(normalizedFile);
            if (!msBuildFilePath.exists()) {
                pwd = build.getWorkspace();
            }
        }

        if (!launcher.isUnix()) {
            args.prepend("cmd.exe", "/C");
            args.add("&&", "exit", "%%ERRORLEVEL%%");
        }

        try {
            listener.getLogger().println(String.format("Executing the command %s from %s", args.toStringWithQuote(), pwd));
            // Parser to find the number of Warnings/Errors
            MsBuildConsoleParser mbcp = new MsBuildConsoleParser(listener.getLogger(), build.getCharset());
            // Launch the msbuild.exe
            int r = launcher.launch().cmds(args).envs(env).stdout(mbcp).pwd(pwd).join();
            // Check the number of warnings
            if (unstableIfWarnings && mbcp.getNumberOfWarnings() > 0) {
                listener.getLogger().println("> Set build UNSTABLE because there are warnings.");
                build.setResult(Result.UNSTABLE);
            }
            // Return the result of the compilation
            return continueOnBuildFailure ? true : (r == 0);
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            build.setResult(Result.FAILURE);
            return false;
        }
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    /**
     * Tokenize a set of arguments, preserving quotes.
     * @param args
     * @return 
     */
    static String[] tokenizeArgs(String args) {
        return Util.tokenize(args);
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @CopyOnWrite
        private volatile MsBuildInstallation[] installations = new MsBuildInstallation[0];

        public DescriptorImpl() {
            super(MsBuildBuilder.class);
            load();
        }

        public String getDisplayName() {
            return "Build a Visual Studio project or solution using MSBuild";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public MsBuildInstallation[] getInstallations() {
            return installations;
        }

        public void setInstallations(MsBuildInstallation... antInstallations) {
            this.installations = antInstallations;
            save();
        }

        public MsBuildInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(MsBuildInstallation.DescriptorImpl.class);
        }
    }
}
