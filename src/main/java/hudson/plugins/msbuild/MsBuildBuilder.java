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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tools.ToolInstallation;
import hudson.util.ArgumentListBuilder;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.nio.charset.Charset;

/**
 * @author kyle.sweeney@valtech.com
 */
public class MsBuildBuilder extends Builder {

    private static final Map<String, Integer> CHARSET_CODE_MAP = new HashMap<>();

    static {
        CHARSET_CODE_MAP.put("UTF-8", 65001);
        CHARSET_CODE_MAP.put("IBM437", 437);
        CHARSET_CODE_MAP.put("IBM850", 850);
        CHARSET_CODE_MAP.put("IBM852", 852);
        CHARSET_CODE_MAP.put("SHIFT_JIS", 932);
        CHARSET_CODE_MAP.put("US-ASCII", 20127);
        CHARSET_CODE_MAP.put("EUC-JP", 20932);
        CHARSET_CODE_MAP.put("ISO-8859-1", 28591);
        CHARSET_CODE_MAP.put("ISO-8859-2", 28592);
        CHARSET_CODE_MAP.put("IBM00858", 858);
        CHARSET_CODE_MAP.put("IBM775", 775);
        CHARSET_CODE_MAP.put("IBM855", 855);
        CHARSET_CODE_MAP.put("IBM857", 857);
        CHARSET_CODE_MAP.put("ISO-8859-4", 28594);
        CHARSET_CODE_MAP.put("ISO-8859-5", 28595);
        CHARSET_CODE_MAP.put("ISO-8859-7", 28597);
        CHARSET_CODE_MAP.put("ISO-8859-9", 28599);
        CHARSET_CODE_MAP.put("ISO-8859-13", 28603);
        CHARSET_CODE_MAP.put("ISO-8859-15", 28605);
        CHARSET_CODE_MAP.put("KOI8-R", 20866);
        CHARSET_CODE_MAP.put("KOI8-U", 21866);
        CHARSET_CODE_MAP.put("UTF-16", 1200);
        CHARSET_CODE_MAP.put("UTF-32", 12000);
        CHARSET_CODE_MAP.put("UTF-32BE", 12001);
        CHARSET_CODE_MAP.put("WINDOWS-1250", 1250);
        CHARSET_CODE_MAP.put("WINDOWS-1251", 1251);
        CHARSET_CODE_MAP.put("WINDOWS-1252", 1252);
        CHARSET_CODE_MAP.put("WINDOWS-1253", 1253);
        CHARSET_CODE_MAP.put("WINDOWS-1254", 1254);
        CHARSET_CODE_MAP.put("WINDOWS-1257", 1257);
        CHARSET_CODE_MAP.put("BIG5", 950);
        CHARSET_CODE_MAP.put("EUC-KR", 51949);
        CHARSET_CODE_MAP.put("GB18030", 54936);
        CHARSET_CODE_MAP.put("GB2312", 936);
        CHARSET_CODE_MAP.put("IBM-THAI", 20838);
        CHARSET_CODE_MAP.put("IBM01140", 1140);
        CHARSET_CODE_MAP.put("IBM01141", 1141);
        CHARSET_CODE_MAP.put("IBM01142", 1142);
        CHARSET_CODE_MAP.put("IBM01143", 1143);
        CHARSET_CODE_MAP.put("IBM01144", 1144);
        CHARSET_CODE_MAP.put("IBM01145", 1145);
        CHARSET_CODE_MAP.put("IBM01146", 1146);
        CHARSET_CODE_MAP.put("IBM01147", 1147);
        CHARSET_CODE_MAP.put("IBM01148", 1148);
        CHARSET_CODE_MAP.put("IBM01149", 1149);
        CHARSET_CODE_MAP.put("IBM037", 37);
        CHARSET_CODE_MAP.put("IBM1026", 1026);
        CHARSET_CODE_MAP.put("IBM273", 20273);
        CHARSET_CODE_MAP.put("IBM277", 20277);
        CHARSET_CODE_MAP.put("IBM278", 20278);
        CHARSET_CODE_MAP.put("IBM280", 20280);
        CHARSET_CODE_MAP.put("IBM284", 20284);
        CHARSET_CODE_MAP.put("IBM285", 20285);
        CHARSET_CODE_MAP.put("IBM297", 20297);
        CHARSET_CODE_MAP.put("IBM420", 20420);
        CHARSET_CODE_MAP.put("IBM424", 20424);
        CHARSET_CODE_MAP.put("IBM500", 500);
        CHARSET_CODE_MAP.put("IBM860", 860);
        CHARSET_CODE_MAP.put("IBM861", 861);
        CHARSET_CODE_MAP.put("IBM863", 863);
        CHARSET_CODE_MAP.put("IBM864", 864);
        CHARSET_CODE_MAP.put("IBM865", 865);
        CHARSET_CODE_MAP.put("IBM869", 869);
        CHARSET_CODE_MAP.put("IBM870", 870);
        CHARSET_CODE_MAP.put("IBM871", 20871);
        CHARSET_CODE_MAP.put("ISO-2022-JP", 50220);
        CHARSET_CODE_MAP.put("ISO-2022-KR", 50225);
        CHARSET_CODE_MAP.put("ISO-8859-3", 28593);
        CHARSET_CODE_MAP.put("ISO-8859-6", 28596);
        CHARSET_CODE_MAP.put("ISO-8859-8", 28598);
        CHARSET_CODE_MAP.put("WINDOWS-1255", 1255);
        CHARSET_CODE_MAP.put("WINDOWS-1256", 1256);
        CHARSET_CODE_MAP.put("WINDOWS-1258", 1258);
    }

    /**
     * GUI fields
     */
    private final String msBuildName;
    private final String msBuildFile;
    private final String cmdLineArgs;
    private final boolean buildVariablesAsProperties;
    @SuppressFBWarnings(value = "UUF_UNUSED_FIELD", justification = "Retain for compatibility")
    private transient boolean continueOnBuilFailure;
    private final boolean continueOnBuildFailure;
    private final boolean unstableIfWarnings;
    private final boolean doNotUseChcpCommand;

    /**
     * When this builder is created in the project configuration step,
     * the builder object will be created from the strings below.
     *
     * @param msBuildName                The Visual Studio logical name
     * @param msBuildFile                The name/location of the MSBuild file
     * @param cmdLineArgs                Whitespace separated list of command line
     *                                   arguments
     * @param buildVariablesAsProperties If true, pass build variables as properties
     *                                   to MSBuild
     * @param continueOnBuildFailure     If true, job will continue dispite of
     *                                   MSBuild build failure
     * @param unstableIfWarnings         If true, job will be unstable if there are
     *                                   warnings
     */
    @Deprecated
    public MsBuildBuilder(String msBuildName, String msBuildFile, String cmdLineArgs,
            boolean buildVariablesAsProperties, boolean continueOnBuildFailure, boolean unstableIfWarnings) {
        // By default, doNotUseChcpCommand=false
        this(msBuildName, msBuildFile, cmdLineArgs, buildVariablesAsProperties, continueOnBuildFailure,
                unstableIfWarnings, false);
    }

    /**
     * When this builder is created in the project configuration step,
     * the builder object will be created from the strings below.
     *
     * @param msBuildName                The Visual Studio logical name
     * @param msBuildFile                The name/location of the MSBuild file
     * @param cmdLineArgs                Whitespace separated list of command line
     *                                   arguments
     * @param buildVariablesAsProperties If true, pass build variables as properties
     *                                   to MSBuild
     * @param continueOnBuildFailure     If true, job will continue dispite of
     *                                   MSBuild build failure
     * @param unstableIfWarnings         If true, job will be unstable if there are
     *                                   warnings
     * @param doNotUseChcpCommand        If true, job will not use chcp command
     *                                   before running msbuild
     */
    @DataBoundConstructor
    public MsBuildBuilder(String msBuildName, String msBuildFile, String cmdLineArgs,
            boolean buildVariablesAsProperties, boolean continueOnBuildFailure, boolean unstableIfWarnings,
            boolean doNotUseChcpCommand) {
        this.msBuildName = msBuildName;
        this.msBuildFile = msBuildFile;
        this.cmdLineArgs = cmdLineArgs;
        this.buildVariablesAsProperties = buildVariablesAsProperties;
        this.continueOnBuildFailure = continueOnBuildFailure;
        this.unstableIfWarnings = unstableIfWarnings;
        this.doNotUseChcpCommand = doNotUseChcpCommand;
    }

    public String getMsBuildFile() {
        return msBuildFile;
    }

    public String getMsBuildName() {
        return msBuildName;
    }

    public String getCmdLineArgs() {
        return cmdLineArgs;
    }

    public boolean getBuildVariablesAsProperties() {
        return buildVariablesAsProperties;
    }

    public boolean getContinueOnBuildFailure() {
        return continueOnBuildFailure;
    }

    public boolean getUnstableIfWarnings() {
        return unstableIfWarnings;
    }

    public boolean getDoNotUseChcpCommand() {
        return doNotUseChcpCommand;
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
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
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

        if (!normalizedArgs.trim().isEmpty())
            args.add(tokenizeArgs(normalizedArgs));

        // Build /P:key1=value1;key2=value2 ...
        Map<String, String> propertiesVariables = getPropertiesVariables(build);
        if (buildVariablesAsProperties && !propertiesVariables.isEmpty()) {
            StringBuilder parameters = new StringBuilder();
            parameters.append("/p:");
            for (Map.Entry<String, String> entry : propertiesVariables.entrySet()) {
                parameters.append(entry.getKey()).append("=").append(entry.getValue()).append(";");
            }
            parameters.delete(parameters.length() - 1, parameters.length());
            args.add(parameters.toString());
        }

        // If a msbuild file is specified, then add it as an argument, otherwise
        // msbuild will search for any file that ends in .proj or .sln
        String normalizedFile = null;
        if (msBuildFile != null && !msBuildFile.trim().isEmpty()) {
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
            if (!doNotUseChcpCommand) {
                final int cpi = getCodePageIdentifier(build.getCharset());
                if (cpi != 0) {
                    args.prepend("chcp", String.valueOf(cpi), "&");
                }
            }

            args.prepend("cmd.exe", "/C", "\"");
            args.add("\"", "&&", "exit", "%%ERRORLEVEL%%");
        } else {
            listener.fatalError("Unable to use this plugin on this kind of operation system");
        }

        try {
            listener.getLogger()
                    .printf("Executing the command %s from %s%n", args.toStringWithQuote(), pwd);
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
            return continueOnBuildFailure || (r == 0);
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            build.setResult(Result.FAILURE);
            return false;
        }
    }

    private Map<String, String> getPropertiesVariables(AbstractBuild<?, ?> build) {

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
    static String getToolFullPath(Launcher launcher, String pathToTool, String execName)
            throws IOException, InterruptedException {
        String fullPathToMsBuild = (pathToTool != null ? pathToTool : "");

        FilePath exec = new FilePath(launcher.getChannel(), fullPathToMsBuild);
        if (exec.isDirectory()) {
            if (!fullPathToMsBuild.endsWith("\\")) {
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

    @NonNull
    static String[] tokenizeArgs(String args) {

        if (args == null) {
            return new String[] {};
        }

        final String[] tokenize = Util.tokenize(args);

        if (args.endsWith("\\")) {
            tokenize[tokenize.length - 1] = tokenize[tokenize.length - 1] + "\\";
        }

        return tokenize;
    }

    @Extension
    @Symbol("msbuild")
    @SuppressFBWarnings(value = "VO_VOLATILE_REFERENCE_TO_ARRAY", justification = "untriaged")
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
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
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

    /**
     * Get the code page identifier for the given charset.
     *
     * @param charset the charset
     * @return the code page identifier
     */
    public static int getCodePageIdentifier(Charset charset) {
        Integer code = CHARSET_CODE_MAP.get(charset.name().toUpperCase(Locale.ENGLISH));
        return code != null ? code : 0;
    }
}
