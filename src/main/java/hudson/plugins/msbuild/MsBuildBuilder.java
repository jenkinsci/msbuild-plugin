/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014, Kyle Sweeney, Gregory Boissinot and other contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.msbuild;

import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.nio.charset.Charset;

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
        DescriptorImpl descriptor = (DescriptorImpl) getDescriptor();
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
            Node node = Computer.currentComputer().getNode();
            if (node != null) {
                ai = ai.forNode(node, listener);
                ai = ai.forEnvironment(env);
                String pathToMsBuild = getToolFullPath(launcher, ai.getHome(), execName);
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
        }

        EnvVars env = build.getEnvironment(listener);
        String normalizedArgs = cmdLineArgs.replaceAll("[\t\r\n]+", " ");
        normalizedArgs = Util.replaceMacro(normalizedArgs, env);
        normalizedArgs = Util.replaceMacro(normalizedArgs, build.getBuildVariables());

        if (normalizedArgs.trim().length() > 0)
            args.add(tokenizeArgs(normalizedArgs));

        //Build /P:key1=value1;key2=value2 ...
        Map<String, String> propertiesVariables = getPropertiesVariables(build);
        if (buildVariablesAsProperties && !propertiesVariables.isEmpty()) {
            StringBuffer parameters = new StringBuffer();
            parameters.append("/p:");
            for (Map.Entry<String, String> entry : propertiesVariables.entrySet()) {
                parameters.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
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
            if (!normalizedFile.isEmpty()) {
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
            final int cpi = getCodePageIdentifier(build.getCharset());
            if(cpi != 0)
                args.prepend("cmd.exe", "/C", "\"", "chcp", String.valueOf(cpi), "&&");
            else
                args.prepend("cmd.exe", "/C", "\"");
            args.add("\"", "&&", "exit", "%%ERRORLEVEL%%");
        }

        try {
            listener.getLogger().println(String.format("Executing the command %s from %s", args.toStringWithQuote(), pwd));
            // Parser to find the number of Warnings/Errors
            MsBuildConsoleParser mbcp = new MsBuildConsoleParser(listener.getLogger(), build.getCharset());
            MSBuildConsoleAnnotator annotator = new MSBuildConsoleAnnotator(listener.getLogger(), build.getCharset());
            // Launch the msbuild.exe
            int r = launcher.launch().cmds(args).envs(env).stdout(mbcp).stdout(annotator).pwd(pwd).join();
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


    private Map<String, String> getPropertiesVariables(AbstractBuild build) {

        Map<String, String> buildVariables = build.getBuildVariables();

        final Set<String> sensitiveBuildVariables = build.getSensitiveBuildVariables();
        if (sensitiveBuildVariables == null || sensitiveBuildVariables.isEmpty()) {
            return buildVariables;
        }

        for (String sensitiveBuildVariable : sensitiveBuildVariables) {
            buildVariables.remove(sensitiveBuildVariable);
        }

        return buildVariables;
    }

    /**
     * Get the full path of the tool to run.
     * If given path is a directory, this will append the executable name.
     */
    static String getToolFullPath(Launcher launcher, String pathToTool, String execName) throws IOException, InterruptedException
    {
        String fullPathToMsBuild = pathToTool;
        
        FilePath exec = new FilePath(launcher.getChannel(), fullPathToMsBuild);
        if (exec.isDirectory())
        {
            if (!fullPathToMsBuild.endsWith("\\"))
            {
                fullPathToMsBuild = fullPathToMsBuild + "\\";
            }

            fullPathToMsBuild = fullPathToMsBuild + execName;
        }
        
        return fullPathToMsBuild;
    }

    @Override
    public Descriptor<Builder> getDescriptor() {
        return super.getDescriptor();
    }

    /**
     * Tokenize a set of arguments, preserving quotes.
     *
     * @param args
     * @return
     */
    static String[] tokenizeArgs(String args) {

        if (args == null) {
            return null;
        }

        final String[] tokenize = Util.tokenize(args);

        if (args.endsWith("\\")) {
            tokenize[tokenize.length - 1] = tokenize[tokenize.length - 1] + "\\";
        }

        return tokenize;
    }

    @Extension @Symbol("msbuild")
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @CopyOnWrite
        private volatile MsBuildInstallation[] installations = new MsBuildInstallation[0];

        public DescriptorImpl() {
            super(MsBuildBuilder.class);
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.MsBuildBuilder_DisplayName();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public MsBuildInstallation[] getInstallations() {
            return Arrays.copyOf(installations, installations.length);
        }

        public void setInstallations(MsBuildInstallation... antInstallations) {
            this.installations = antInstallations;
            save();
        }

        public MsBuildInstallation.DescriptorImpl getToolDescriptor() {
            return ToolInstallation.all().get(MsBuildInstallation.DescriptorImpl.class);
        }
    }

    private static int getCodePageIdentifier(Charset charset) {
        final String s_charset = charset.name();
        if(s_charset.equalsIgnoreCase("utf-8"))            // Unicode
            return 65001;
        else if(s_charset.equalsIgnoreCase("shift_jis") || s_charset.equalsIgnoreCase("windows-31j"))//Japanese
            return 932;
        else if(s_charset.equalsIgnoreCase("us-ascii"))    // Japanese
            return 20127;
        else if(s_charset.equalsIgnoreCase("euc-jp"))      // Japanese
            return 20932;
        else if(s_charset.equalsIgnoreCase("iso-8859-1"))  // Latin 1
            return 28591;
        else if(s_charset.equalsIgnoreCase("iso-8859-2"))  // Latin 2
            return 28592;
        else
            return 0;
    }
}
