package hudson.plugins.msbuild;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.console.ConsoleNote;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.TextParameterDefinition;
import hudson.tasks.Builder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Jonathan Zimmerman
 */
class MsBuildBuilderTest {

    // --- pure helpers -------------------------------------------------------

    @Test
    void shouldStripQuotedArguments() {
        String[] tokenizedArgs = MsBuildBuilder.tokenizeArgs("/p:Platform=\"Any CPU\"");
        assertArrayEquals(new String[] {"/p:Platform=Any CPU"}, tokenizedArgs);
    }

    @Test
    void shouldSplitArguments() {
        String[] tokenizedArgs = MsBuildBuilder.tokenizeArgs("/t:Build /p:Configuration=Debug");
        assertArrayEquals(new String[] {"/t:Build", "/p:Configuration=Debug"}, tokenizedArgs);
    }

    @Test
    void endEscapedCharacter() {
        final String oneArgumentsWithEndBackslash = "\\\\RemoteServerName\\OfficialBuilds\\Published\\";
        String[] tokenizedArgs = MsBuildBuilder.tokenizeArgs(oneArgumentsWithEndBackslash);
        assertArrayEquals(new String[] {oneArgumentsWithEndBackslash}, tokenizedArgs);
    }

    @Test
    void backslashesAreLiteral() {
        assertArrayEquals(new String[] {"/p:OutDir=bin\\", "/m"}, MsBuildBuilder.tokenizeArgs("/p:OutDir=bin\\ /m"));
        assertArrayEquals(new String[] {"/p:Pub=\\\\srv\\share", "/p:A=x\\\\y"},
                MsBuildBuilder.tokenizeArgs("/p:Pub=\\\\srv\\share /p:A=x\\\\y"));
    }

    @Test
    void backslashesBeforeQuoteFollowWindowsRules() {
        assertArrayEquals(new String[] {"/p:DefineConstants=\"A;B\"", "/m"},
                MsBuildBuilder.tokenizeArgs("/p:DefineConstants=\\\"A;B\\\" /m"));
        assertArrayEquals(new String[] {"/p:X=a \"q\" b"}, MsBuildBuilder.tokenizeArgs("/p:X=\"a \\\"q\\\" b\""));
        assertArrayEquals(new String[] {"/p:Out=C:\\a b\\", "/m"},
                MsBuildBuilder.tokenizeArgs("\"/p:Out=C:\\a b\\\\\" /m"));
        assertArrayEquals(new String[] {"a\\\"b c"}, MsBuildBuilder.tokenizeArgs("a\\\\\\\"b\" c\""));
        assertArrayEquals(new String[] {"\\\\"}, MsBuildBuilder.tokenizeArgs("\\\\"));
    }

    @Test
    void onlyDoubleQuotesGroup() {
        assertArrayEquals(new String[] {"/p:A='x", "y'", "", "a b"},
                MsBuildBuilder.tokenizeArgs("  /p:A='x\ty'  \"\"  \"a b\" "));
    }

    @Test
    void tokenizeNullGivesEmptyArray() {
        assertEquals(0, MsBuildBuilder.tokenizeArgs(null).length);
    }

    @Test
    void testValidCharset() {
        assertEquals(65001, MsBuildBuilder.getCodePageIdentifier(StandardCharsets.UTF_8));
    }

    @Test
    void testInvalidCharset() {
        assertEquals(0, MsBuildBuilder.getCodePageIdentifier(Charset.forName("ISO-8859-16")),
                "Code page for unrecognized charset should be 0");
    }

    @Test
    void utf16HasNoChcpCodePage() {
        // chcp rejects 1200/12000/12001, and lines cannot be split on '\n' bytes
        assertEquals(0, MsBuildBuilder.getCodePageIdentifier(StandardCharsets.UTF_16));
        assertEquals(0, MsBuildBuilder.getCodePageIdentifier(Charset.forName("UTF-32")));
    }

    @Test
    void testNullCharset() {
        assertThrows(NullPointerException.class, () -> MsBuildBuilder.getCodePageIdentifier(null));
    }

    @Test
    void testCaseInsensitivity() {
        // Charset.name() is lower/mixed case for these, the map keys are upper case
        assertEquals("windows-1252", Charset.forName("windows-1252").name());
        assertEquals(1252, MsBuildBuilder.getCodePageIdentifier(Charset.forName("windows-1252")));
        assertEquals(932, MsBuildBuilder.getCodePageIdentifier(Charset.forName("Shift_JIS")));
    }

    @Test
    void deprecatedConstructorDefaultsChcpToEnabled() {
        @SuppressWarnings("deprecation")
        MsBuildBuilder builder = new MsBuildBuilder("vs", "a.sln", "/m", true, true, true);
        assertFalse(builder.getDoNotUseChcpCommand());
        assertEquals("vs", builder.getMsBuildName());
        assertEquals("a.sln", builder.getMsBuildFile());
        assertEquals("/m", builder.getCmdLineArgs());
        assertTrue(builder.getBuildVariablesAsProperties());
        assertTrue(builder.getContinueOnBuildFailure());
        assertTrue(builder.getUnstableIfWarnings());
    }

    @Test
    void toolFullPathAppendsExeForDirectory(@TempDir Path dir) throws Exception {
        Launcher launcher = new Launcher.LocalLauncher(null);
        assertEquals(dir + "\\msbuild.exe", MsBuildBuilder.getToolFullPath(launcher, dir.toString(), "msbuild.exe"));
    }

    @Test
    void toolFullPathKeepsTrailingBackslashOfDirectory(@TempDir Path dir) throws Exception {
        String vs = Files.createDirectory(dir.resolve("vs")) + "\\";
        assertEquals(vs + "msbuild.exe",
                MsBuildBuilder.getToolFullPath(new Launcher.LocalLauncher(null), vs, "msbuild.exe"));
    }

    @Test
    void toolFullPathKeepsFile(@TempDir Path dir) throws Exception {
        Path exe = Files.createFile(dir.resolve("msbuild.exe"));
        Launcher launcher = new Launcher.LocalLauncher(null);
        assertEquals(exe.toString(), MsBuildBuilder.getToolFullPath(launcher, exe.toString(), "msbuild.exe"));
    }

    @Test
    void toolFullPathOfNullIsTreatedAsCurrentDirectory() throws Exception {
        // "" resolves to the working directory, which is a directory, so the exe name is appended
        assertEquals("\\msbuild.exe",
                MsBuildBuilder.getToolFullPath(new Launcher.LocalLauncher(null), null, "msbuild.exe"));
    }

    // --- configuration ------------------------------------------------------

    @Test
    @WithJenkins
    void configRoundtrip(JenkinsRule r) throws Exception {
        MsBuildInstallation vs = new MsBuildInstallation("VS2022", "C:\\MSBuild", Collections.emptyList(), "/m");
        descriptor(r).setInstallations(vs);
        FreeStyleProject project = r.createFreeStyleProject();
        MsBuildBuilder before = new MsBuildBuilder("VS2022", "src\\app.sln", "/t:Rebuild /p:Configuration=Release",
                true, true, true, true);
        project.getBuildersList().add(before);

        r.configRoundtrip(project);

        MsBuildBuilder after = project.getBuildersList().get(MsBuildBuilder.class);
        assertNotSame(before, after);
        r.assertEqualDataBoundBeans(before, after);
        assertEquals(vs.getName(), after.getMsBuild().getName());
    }

    @Test
    @WithJenkins
    @LocalData
    void legacyConfigLoads(JenkinsRule r) throws Exception {
        // job saved by plugin 1.26, before doNotUseChcpCommand existed
        FreeStyleProject project = r.jenkins.getItemByFullName("MsBuildBuilder-1-26", FreeStyleProject.class);
        MsBuildBuilder builder = project.getBuildersList().get(MsBuildBuilder.class);
        assertEquals("(Default)", builder.getMsBuildName());
        assertEquals("project.proj", builder.getMsBuildFile());
        assertEquals("/verbosity:detailed", builder.getCmdLineArgs());
        assertFalse(builder.getBuildVariablesAsProperties());
        assertFalse(builder.getContinueOnBuildFailure());
        assertFalse(builder.getUnstableIfWarnings());
        assertFalse(builder.getDoNotUseChcpCommand());
        assertNull(builder.getMsBuild(), "no installation named (Default) is configured");

        r.configRoundtrip(project);
        r.assertEqualDataBoundBeans(builder, project.getBuildersList().get(MsBuildBuilder.class));
    }

    @Test
    @WithJenkins
    void descriptorBasics(JenkinsRule r) {
        MsBuildBuilder.DescriptorImpl d = descriptor(r);
        assertEquals("Build a Visual Studio project or solution using MSBuild", d.getDisplayName());
        assertTrue(d.isApplicable(FreeStyleProject.class));
        assertSame(r.jenkins.getDescriptorByType(MsBuildInstallation.DescriptorImpl.class), d.getToolDescriptor());

        MsBuildInstallation vs = new MsBuildInstallation("VS", "C:\\MSBuild", Collections.emptyList(), null);
        d.setInstallations(vs);
        MsBuildInstallation[] copy = d.getInstallations();
        copy[0] = null;
        assertSame(vs, d.getInstallations()[0], "getInstallations must return a defensive copy");
    }

    // --- perform ------------------------------------------------------------

    @Test
    @WithJenkins
    void defaultCommandLine(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, "app.sln", "/t:Build", false, false, false, false));

        r.assertBuildStatusSuccess(run.build);
        assertEquals(windows("\"chcp", "65001", "&", "msbuild.exe", "/t:Build", "app.sln",
                "&&", "exit", "%%ERRORLEVEL%%\""), run.launcher.lastCommand());
        r.assertLogContains("Path To MSBuild.exe: msbuild.exe", run.build);
    }

    @Test
    @WithJenkins
    void doNotUseChcpCommand(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, "", "", false, false, false, true));

        assertEquals(windows("\"msbuild.exe", "&&", "exit", "%%ERRORLEVEL%%\""),
                run.launcher.lastCommand());
    }

    @Test
    @WithJenkins
    void unknownCharsetSkipsChcp(JenkinsRule r) throws Exception {
        MsBuildBuilder builder = new MsBuildBuilder(null, null, "", false, false, false, false);
        Run run = run(r, builder, null, Charset.forName("ISO-8859-16"));

        assertFalse(String.join(" ", run.launcher.lastCommand()).contains("chcp"));
    }

    @Test
    @WithJenkins
    void usesConfiguredInstallation(JenkinsRule r, @TempDir Path dir) throws Exception {
        Path exe = Files.createFile(dir.resolve("MSBuild.exe"));
        descriptor(r).setInstallations(
                new MsBuildInstallation("VS", exe.toString(), Collections.emptyList(), "/nologo /m"));

        Run run = run(r, new MsBuildBuilder("VS", null, "", false, false, false, true));

        r.assertBuildStatusSuccess(run.build);
        assertEquals(windows("\"" + exe, "/nologo", "/m", "&&", "exit", "%%ERRORLEVEL%%\""),
                run.launcher.lastCommand());
        r.assertLogContains("Path To MSBuild.exe: " + exe, run.build);
    }

    @Test
    @WithJenkins
    void installationWithoutDefaultArgs(JenkinsRule r, @TempDir Path dir) throws Exception {
        Path exe = Files.createFile(dir.resolve("MSBuild.exe"));
        descriptor(r).setInstallations(new MsBuildInstallation("VS", exe.toString(), Collections.emptyList(), null));

        Run run = run(r, new MsBuildBuilder("VS", null, "", false, false, false, true));

        assertEquals(windows("\"" + exe, "&&", "exit", "%%ERRORLEVEL%%\""),
                run.launcher.lastCommand());
    }

    @Test
    @WithJenkins
    void noInstallationNameIgnoresConfiguredOnes(JenkinsRule r, @TempDir Path dir) throws Exception {
        Path exe = Files.createFile(dir.resolve("MSBuild.exe"));
        descriptor(r).setInstallations(new MsBuildInstallation("VS", exe.toString(), Collections.emptyList(), null));

        Run run = run(r, new MsBuildBuilder(null, null, "", false, false, false, true));

        assertEquals(windows("\"msbuild.exe", "&&", "exit", "%%ERRORLEVEL%%\""), run.launcher.lastCommand());
    }

    @Test
    @WithJenkins
    void failsWhenNodeIsGone(JenkinsRule r, @TempDir Path dir) throws Exception {
        Path exe = Files.createFile(dir.resolve("MSBuild.exe"));
        descriptor(r).setInstallations(new MsBuildInstallation("VS", exe.toString(), Collections.emptyList(), null));
        MsBuildBuilder builder = new MsBuildBuilder("VS", null, "", false, false, false, true);
        FreeStyleProject p = r.createFreeStyleProject();
        RecordingLauncher[] launcher = new RecordingLauncher[1];
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher l, BuildListener listener)
                    throws InterruptedException, IOException {
                launcher[0] = new RecordingLauncher(listener);
                // static mocks are per thread, so this has to happen on the executor
                Computer gone = org.mockito.Mockito.spy(Computer.currentComputer());
                org.mockito.Mockito.doReturn(null).when(gone).getNode();
                try (var computer = org.mockito.Mockito.mockStatic(Computer.class)) {
                    computer.when(Computer::currentComputer).thenReturn(gone);
                    return builder.perform(build, launcher[0], listener);
                }
            }
        });

        FreeStyleBuild build = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));

        r.assertLogContains("The node of this build is no longer available", build);
        assertTrue(launcher[0].commands.isEmpty());
    }

    @Test
    @WithJenkins
    void selectsInstallationByName(JenkinsRule r, @TempDir Path dir) throws Exception {
        Path first = Files.createFile(dir.resolve("first.exe"));
        Path second = Files.createFile(dir.resolve("second.exe"));
        descriptor(r).setInstallations(
                new MsBuildInstallation("A", first.toString(), Collections.emptyList(), null),
                new MsBuildInstallation("B", second.toString(), Collections.emptyList(), null));

        Run run = run(r, new MsBuildBuilder("B", null, "", false, false, false, true));

        assertTrue(line(run).startsWith("\"" + second + " "), line(run));
    }

    @Test
    @WithJenkins
    void missingExecutableFailsBuild(JenkinsRule r, @TempDir Path dir) throws Exception {
        descriptor(r).setInstallations(new MsBuildInstallation("VS", dir.toString(), Collections.emptyList(), null));

        Run run = run(r, new MsBuildBuilder("VS", null, "", false, false, false, false));

        r.assertBuildStatus(Result.FAILURE, run.build);
        r.assertLogContains(dir + "\\msbuild.exe doesn't exist", run.build);
        assertTrue(run.launcher.commands.isEmpty());
    }

    @Test
    @WithJenkins
    void expandsVariablesAndNormalizesWhitespace(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("job");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("CONFIG", "Release")));
        // JOB_NAME is only an environment variable, CONFIG also a build variable
        Run run = run(r, p, new MsBuildBuilder(null, "${JOB_NAME}\\${CONFIG}\t.sln",
                "/p:Configuration=${CONFIG}\t\r\n/p:Out=${JOB_NAME} \"/p:Title=a\tb\"", false, false, false, true), null);

        assertEquals(windows("\"msbuild.exe", "/p:Configuration=Release", "/p:Out=job",
                "\"/p:Title=a b\"", "\"job\\Release .sln\"", "&&", "exit", "%%ERRORLEVEL%%\""),
                run.launcher.lastCommand());
    }

    @Test
    @WithJenkins
    void expandsWindowsVariables(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("job");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("B", "%PATH%")));
        // cmd.exe does not expand the quoted %, so the plugin has to; values and unknown names stay as they are
        Run run = run(r, p, new MsBuildBuilder(null, "%JOB_BASE_NAME%.sln", "/p:B=%b% /p:U=%NO_SUCH_VAR%",
                false, false, false, true), null);

        assertEquals(windows("\"msbuild.exe", "\"/p:B=%\"P\"ATH%\"", "\"/p:U=%\"N\"O_SUCH_VAR%\"", "job.sln",
                "&&", "exit", "%%ERRORLEVEL%%\""), run.launcher.lastCommand());
    }

    @Test
    @WithJenkins
    void expandsWindowsVariablesAsUsersWroteThem(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject("job");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("ROOT", "C:\\Program Files\\src"),
                new StringParameterDefinition("SvnBranchName", "trunk"),
                new StringParameterDefinition("SITE", "Default Web Site"),
                new StringParameterDefinition("DEST", "C:\\deploy")));
        // from job configs attached to JENKINS-15029, JENKINS-11069 and JENKINS-7497, including the stray quote
        Run run = run(r, p, new MsBuildBuilder(null, "\"%root%\\%SvnBranchName%\\wbuilder_%JOB_NAME%_%SvnBranchName%.sln\"",
                "/t:Clean,Build /property:PublishProfile=Release;DeployIisAppPath=\"%SITE%/services/soap\";"
                        + "PublishDestination=%DEST%\";VisualStudioVersion=11.0",
                false, false, false, true), null);

        assertEquals(windows("\"msbuild.exe", "\"/t:Clean,Build\"",
                "\"/property:PublishProfile=Release;DeployIisAppPath=Default Web Site/services/soap;"
                        + "PublishDestination=C:\\deploy;VisualStudioVersion=11.0\"",
                "\"C:\\Program Files\\src\\trunk\\wbuilder_job_trunk.sln\"", "&&", "exit", "%%ERRORLEVEL%%\""),
                run.launcher.lastCommand());
    }

    @Test
    @WithJenkins
    void windowsSecretIsMaskedInLog(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new PasswordParameterDefinition("SECRET", "hunter2", null)));
        Run run = run(r, p, new MsBuildBuilder(null, null, "/p:Token=%secret% /m", false, false, false, true), null);

        assertTrue(line(run).contains(" /p:Token=hunter2 "), "msbuild still gets the value");
        r.assertLogNotContains("hunter2", run.build);
    }

    @Test
    @WithJenkins
    void defaultArgsKeepSecretsAndLineBreaksOutOfTheCommand(JenkinsRule r, @TempDir Path dir) throws Exception {
        Path exe = Files.createFile(dir.resolve("MSBuild.exe"));
        descriptor(r).setInstallations(new MsBuildInstallation("VS", exe.toString(), Collections.emptyList(),
                "/p:Token=%SECRET% /p:Note=%NOTE%"));
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new PasswordParameterDefinition("SECRET", "hunter2", null),
                new TextParameterDefinition("NOTE", "a\nwhoami", null)));

        Run run = run(r, p, new MsBuildBuilder("VS", null, "", false, false, false, true), null);

        assertTrue(line(run).contains(" /p:Token=hunter2 \"/p:Note=a whoami\" "), line(run));
        r.assertLogNotContains("hunter2", run.build);
    }

    @Test
    @WithJenkins
    void expandsWindowsVariablesInDefaultArgs(JenkinsRule r, @TempDir Path dir) throws Exception {
        Path exe = Files.createFile(dir.resolve("MSBuild.exe"));
        descriptor(r).setInstallations(new MsBuildInstallation("VS", exe.toString(), Collections.emptyList(),
                "/p:Name=%JOB_BASE_NAME% %JOB_BASE_NAME:~99% /p:Raw=${JOB_BASE_NAME}"));

        Run run = run(r, new MsBuildBuilder("VS", null, "", false, false, false, true));

        r.assertBuildStatusSuccess(run.build);
        assertTrue(line(run).contains(" /p:Name=" + run.build.getParent().getName() + " /p:Raw=${JOB_BASE_NAME} "),
                line(run));
    }

    @Test
    @WithJenkins
    void buildVariablesAsPropertiesExcludesSecrets(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("A", "1"),
                new PasswordParameterDefinition("SECRET", "hunter2", null)));
        Run run = run(r, p, new MsBuildBuilder(null, null, "", true, false, false, true), null);

        assertTrue(line(run).contains(" /p:A=1 "), line(run));
        assertFalse(line(run).contains("hunter2"), line(run));
    }

    @Test
    void trailingBackslashesBeforeClosingQuoteAreDoubled() {
        assertEquals("\"/p:OutDir=C:\\a b\\\\\"", MsBuildBuilder.doubleBackslashesBeforeClosingQuote("\"/p:OutDir=C:\\a b\\\""));
        assertEquals("\"a b\\\\\\\\\"", MsBuildBuilder.doubleBackslashesBeforeClosingQuote("\"a b\\\\\""));
        assertEquals("\"a b\"", MsBuildBuilder.doubleBackslashesBeforeClosingQuote("\"a b\""));
        assertEquals("C:\\out\\", MsBuildBuilder.doubleBackslashesBeforeClosingQuote("C:\\out\\"));
        assertEquals("\\\\\\\\\"", MsBuildBuilder.doubleBackslashesBeforeClosingQuote("\\\\\""), "only backslashes");
    }

    @Test
    @WithJenkins
    void outDirWithTrailingBackslash(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, "x.sln",
                "/p:Configuration=Release;OutDir=C:\\out\\ \"/p:Docs=C:\\a b\\\\\" /p:Plain=bin\\", false, false, false, true));

        // msbuild.exe reads \\" as \ followed by the closing quote
        assertEquals(windows("\"msbuild.exe", "\"/p:Configuration=Release;OutDir=C:\\out\\\\\"",
                "\"/p:Docs=C:\\a b\\\\\"", "/p:Plain=bin\\", "x.sln", "&&", "exit", "%%ERRORLEVEL%%\""),
                run.launcher.lastCommand());
    }

    @Test
    @WithJenkins
    void escapedQuotesReachMsBuild(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, null, "/p:DefineConstants=\\\"A;B\\\"", false, false, false, true));

        // "" inside quotes is a literal quote, so msbuild.exe gets /p:DefineConstants="A;B"
        assertEquals(windows("\"msbuild.exe", "\"/p:DefineConstants=\"\"A;B\"\"\"", "&&", "exit", "%%ERRORLEVEL%%\""),
                run.launcher.lastCommand());
    }

    @Test
    @WithJenkins
    void quotedOrBlankProjectFile(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, "\"C:\\a b\\x.sln\"", "", false, false, false, true));
        assertEquals(windows("\"msbuild.exe", "\"C:\\a b\\x.sln\"", "&&", "exit", "%%ERRORLEVEL%%\""),
                run.launcher.lastCommand());

        // a lone or unbalanced quote is not a quoted path and stays part of the name
        run = run(r, new MsBuildBuilder(null, "\"x.sln", "", false, false, false, true));
        assertEquals(windows("\"msbuild.exe", "\"\"\"x.sln\"", "&&", "exit", "%%ERRORLEVEL%%\""),
                run.launcher.lastCommand());

        run = run(r, new MsBuildBuilder(null, "\"", "", false, false, false, true));
        assertEquals(windows("\"msbuild.exe", "\"\"\"\"", "&&", "exit", "%%ERRORLEVEL%%\""),
                run.launcher.lastCommand());

        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("F", " ")));
        run = run(r, p, new MsBuildBuilder(null, "${F}", "", false, false, false, true), null);
        assertEquals(windows("\"msbuild.exe", "&&", "exit", "%%ERRORLEVEL%%\""), run.launcher.lastCommand());
    }

    @Test
    @WithJenkins
    void parameterValueStaysOneArgument(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("VERSION", "1 -logger:X,\\\\host\\evil.dll"),
                new StringParameterDefinition("NOTE", "a\r\nb"), new StringParameterDefinition("EMPTY", "")));
        Run run = run(r, p, new MsBuildBuilder(null, null, "/p:V=${VERSION} /p:N=${NOTE} ${EMPTY} /m",
                false, false, false, true), null);

        assertEquals(windows("\"msbuild.exe", "\"/p:V=1 -logger:X,\\\\host\\evil.dll\"", "\"/p:N=a b\"", "/m",
                "&&", "exit", "%%ERRORLEVEL%%\""), run.launcher.lastCommand());
    }

    @Test
    @WithJenkins
    void secretsInArgumentsAreMaskedInLog(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new PasswordParameterDefinition("SECRET", "hunter2", null)));
        Run run = run(r, p, new MsBuildBuilder(null, null, "/p:Token=${SECRET} /m", false, false, false, true), null);

        assertTrue(line(run).contains(" /p:Token=hunter2 "), "msbuild still gets the value");
        r.assertLogNotContains("hunter2", run.build);
        r.assertLogContains("****** /m", run.build);
    }

    @Test
    void referencesAny() {
        Set<String> secrets = Set.of("SECRET");
        assertTrue(MsBuildBuilder.referencesAny("/p:T=${SECRET}", secrets));
        assertTrue(MsBuildBuilder.referencesAny("/p:T=$SECRET", secrets));
        assertFalse(MsBuildBuilder.referencesAny("/p:T=${SECRETS} $OTHER", secrets));
        assertTrue(MsBuildBuilder.referencesAny("/p:T=%SECRET%", secrets));
        assertFalse(MsBuildBuilder.referencesAny("/p:T=%SECRETS%", secrets));
        assertTrue(MsBuildBuilder.referencesAny("/p:T=%secret%", secrets));
        assertTrue(MsBuildBuilder.referencesAny("/p:T=%Secret:~0,3%", secrets));
        // %x% may be unknown, then %SECRET% expands
        assertTrue(MsBuildBuilder.referencesAny("/p:T=%x%SECRET%", secrets));
        assertTrue(MsBuildBuilder.referencesAny("/p:T=%${NAME}%", secrets));
        assertFalse(MsBuildBuilder.referencesAny("/p:T=%${NAME}%", Set.of()));
    }

    @Test
    void expandWindowsVariables() {
        assertEquals("MyProject.sln", windowsVariables("%name%.sln"));
        assertEquals("%NO_SUCH%", windowsVariables("%NO_SUCH%"));
        assertEquals("%NO_SUCH:~1%", windowsVariables("%NO_SUCH:~1%"));
        assertEquals("Proj", windowsVariables("%NAME:~2,4%"));
        assertEquals("ject", windowsVariables("%NAME:~-4%"));
        assertEquals("MyProj", windowsVariables("%NAME:~0,-3%"));
        assertEquals("", windowsVariables("%NAME:~20,2%"));
        assertEquals("", windowsVariables("%NAME:~4,-9%"));
        assertEquals("MyProject", windowsVariables("%NAME:~-20,99%"));
        assertEquals("MyApp", windowsVariables("%NAME:project=App%"));
        assertEquals("Mx$1Project", windowsVariables("%NAME:y=x$1%"));
        assertEquals("Xject", windowsVariables("%NAME:*pro=X%"));
        assertEquals("MyProject", windowsVariables("%NAME:*zz=X%"));
        assertEquals("MyProject", windowsVariables("%NAME:*=X%"));
        assertEquals("%NAME:=X%", windowsVariables("%NAME:=X%"));
        assertEquals("%NAME:x%", windowsVariables("%NAME:x%"));
        assertEquals(".sln", windowsVariables("%EMPTY%.sln"));
        assertEquals("50%", windowsVariables("50%"));
        assertEquals("50%;Out=MyProject", windowsVariables("50%;Out=%NAME%"));
        assertEquals("%NO_SUCH" + "MyProject", windowsVariables("%NO_SUCH%NAME%"));
        assertEquals("MyProjectNAME%", windowsVariables("%NAME%NAME%"));
        // only the text around the variables goes through the ${NAME} expansion
        assertEquals("[a]MyProject[b]", MsBuildBuilder.expandWindowsVariables("a%NAME%b", ENV, t -> "[" + t + "]"));
        assertEquals("[a%NO_SUCH%b]", MsBuildBuilder.expandWindowsVariables("a%NO_SUCH%b", ENV, t -> "[" + t + "]"));
        // %${X}% looks up the variable named by X, like cmd.exe after ${X} was expanded
        UnaryOperator<String> x = t -> t.replace("${X}", "NAME").replace("${N}", "4");
        assertEquals("MyProject Proj", MsBuildBuilder.expandWindowsVariables("%${X}% %NAME:~2,${N}%", ENV, x));
    }

    private static final EnvVars ENV = new EnvVars("NAME", "MyProject", "EMPTY", "");

    private static String windowsVariables(String template) {
        return MsBuildBuilder.expandWindowsVariables(template, ENV, UnaryOperator.identity());
    }

    @Test
    @WithJenkins
    void shortSecretDoesNotMaskOtherArguments(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new PasswordParameterDefinition("PIN", "1", null)));
        Run run = run(r, p, new MsBuildBuilder(null, null, "/p:Pin=${PIN} /p:A=1 /m:1", false, false, false, true),
                null);

        r.assertLogContains("****** /p:A=1 /m:1", run.build);
    }

    @Test
    @WithJenkins
    void secretInProjectFileIsMasked(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(new PasswordParameterDefinition("TOKEN", "hunter2", null)));
        Run run = run(r, p, new MsBuildBuilder(null, "\\\\srv\\${TOKEN}\\app.sln", "", false, false, false, true),
                null);

        assertTrue(line(run).contains("hunter2"), "msbuild still gets the value");
        r.assertLogNotContains("hunter2", run.build);
    }

    @Test
    @WithJenkins
    void parameterValuesCannotInjectCommands(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("A", "x&calc"), new StringParameterDefinition("B", "%PATH%")));
        Run run = run(r, p, new MsBuildBuilder(null, null, "/p:C=${A}|more", true, false, false, true), null);

        // quoted, so cmd.exe passes & and | through; %"P"ATH% is not expanded, msbuild drops the quotes
        assertTrue(line(run).contains(" \"/p:C=x&calc|more\" "), line(run));
        assertTrue(line(run).contains(" \"/p:A=x&calc;B=%\"P\"ATH%\" "), line(run));
    }

    @Test
    @WithJenkins
    void buildVariablesAsPropertiesWithoutVariablesAddsNothing(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, null, "", true, false, false, true));

        assertFalse(String.join(" ", run.launcher.lastCommand()).contains("/p:"));
    }

    @Test
    @WithJenkins
    void buildVariablesAsPropertiesJoinsAllVariables(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("A", "1"), new StringParameterDefinition("B", "2")));
        Run run = run(r, p, new MsBuildBuilder(null, null, "", true, false, false, true), null);

        assertTrue(line(run).contains(" \"/p:A=1;B=2\" "), line(run));
    }

    @Test
    @WithJenkins
    void runsInModuleRootWhenProjectFileExists(JenkinsRule r) throws Exception {
        FreeStyleProject p = r.createFreeStyleProject();
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws IOException, InterruptedException {
                build.getWorkspace().child("app.sln").write("", "UTF-8");
                return true;
            }
        });
        Run run = run(r, p, new MsBuildBuilder(null, "app.sln", "", false, false, false, true), null);

        assertEquals(run.build.getModuleRoot(), run.launcher.dirs.get(0));
    }

    @Test
    @WithJenkins
    void runsInWorkspaceWhenProjectFileIsMissing(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, "missing.sln", "", false, false, false, true));

        assertEquals(run.build.getWorkspace(), run.launcher.dirs.get(0));
    }

    @Test
    @WithJenkins
    void nonZeroExitFailsBuild(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, null, "", false, false, false, true), l -> l.exitCode = 1);

        r.assertBuildStatus(Result.FAILURE, run.build);
    }

    @Test
    @WithJenkins
    void continueOnBuildFailure(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, null, "", false, true, false, true), l -> l.exitCode = 1);

        r.assertBuildStatusSuccess(run.build);
    }

    @Test
    @WithJenkins
    void unstableIfWarnings(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, null, "", false, false, true, true),
                l -> l.output = "Foo.cs(1,2): warning CS0168: unused\nBuild succeeded.\n    1 Warning(s)\n    0 Error(s)\n");

        r.assertBuildStatus(Result.UNSTABLE, run.build);
        r.assertLogContains("> Set build UNSTABLE because there are warnings.", run.build);
        r.assertLogContains("    1 Warning(s)", run.build);
        try (InputStream log = run.build.getLogInputStream()) {
            assertTrue(new String(log.readAllBytes(), StandardCharsets.UTF_8).contains(ConsoleNote.PREAMBLE_STR),
                    "output goes through the annotator");
        }
    }

    @Test
    @WithJenkins
    void unstableIfWarningsWithoutSummary(JenkinsRule r) throws Exception {
        // /clp:NoSummary or a localized summary ("1 Warnung(en)")
        Run run = run(r, new MsBuildBuilder(null, null, "", false, false, true, true),
                l -> l.output = "Foo.cs(1,2): warning CS0168: unused\n    1 Warnung(en)\n");

        r.assertBuildStatus(Result.UNSTABLE, run.build);
    }

    @Test
    @WithJenkins
    void lastLineWithoutNewlineIsProcessed(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, null, "", false, false, true, true),
                l -> l.output = "Build succeeded.\n    2 Warning(s)");

        r.assertBuildStatus(Result.UNSTABLE, run.build);
        r.assertLogContains("    2 Warning(s)", run.build);
    }

    @Test
    @WithJenkins
    void warningsAreIgnoredUnlessRequested(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, null, "", false, false, false, true),
                l -> l.output = "    1 Warning(s)\n");

        r.assertBuildStatusSuccess(run.build);
    }

    @Test
    @WithJenkins
    void unstableIfWarningsStaysSuccessWithoutWarnings(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, null, "", false, false, true, true),
                l -> l.output = "    0 Warning(s)\n");

        r.assertBuildStatusSuccess(run.build);
    }

    @Test
    @WithJenkins
    void unixAgentIsReported(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, null, "", false, false, false, false), l -> l.unix = true);

        r.assertBuildStatus(Result.FAILURE, run.build);
        r.assertLogContains("Unable to use this plugin on this kind of operating system", run.build);
        assertTrue(run.launcher.commands.isEmpty());
    }

    @Test
    @WithJenkins
    void launchFailureFailsBuild(JenkinsRule r) throws Exception {
        Run run = run(r, new MsBuildBuilder(null, null, "", false, true, false, true),
                l -> l.failure = new IOException("boom"));

        // continueOnBuildFailure does not mask a launch failure
        r.assertBuildStatus(Result.FAILURE, run.build);
    }

    // --- helpers ------------------------------------------------------------

    private static MsBuildBuilder.DescriptorImpl descriptor(JenkinsRule r) {
        return r.jenkins.getDescriptorByType(MsBuildBuilder.DescriptorImpl.class);
    }

    /** The command as launched: everything after {@code /C} is one argument. */
    private static List<String> windows(String... parts) {
        return List.of("cmd.exe", "/C", String.join(" ", parts));
    }

    private static String line(Run run) {
        List<String> cmd = run.launcher.lastCommand();
        assertEquals(3, cmd.size(), cmd.toString());
        return cmd.get(2);
    }

    private record Run(FreeStyleBuild build, RecordingLauncher launcher) {
    }

    private interface Setup {
        void apply(RecordingLauncher launcher);
    }

    private static Run run(JenkinsRule r, MsBuildBuilder builder) throws Exception {
        return run(r, r.createFreeStyleProject(), builder, null);
    }

    private static Run run(JenkinsRule r, MsBuildBuilder builder, Setup setup) throws Exception {
        return run(r, r.createFreeStyleProject(), builder, setup);
    }

    private static Run run(JenkinsRule r, MsBuildBuilder builder, Setup setup, Charset charset) throws Exception {
        return run(r, r.createFreeStyleProject(), builder, setup, charset);
    }

    private static Run run(JenkinsRule r, FreeStyleProject p, MsBuildBuilder builder, Setup setup) throws Exception {
        return run(r, p, builder, setup, null);
    }

    /** Runs {@code builder} inside a real build, but with a launcher that only records the command. */
    private static Run run(JenkinsRule r, FreeStyleProject p, MsBuildBuilder builder, Setup setup, Charset charset)
            throws Exception {
        RecordingLauncher[] launcher = new RecordingLauncher[1];
        Builder wrapper = new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher l, BuildListener listener)
                    throws InterruptedException, IOException {
                launcher[0] = new RecordingLauncher(listener);
                if (setup != null) {
                    setup.apply(launcher[0]);
                }
                AbstractBuild<?, ?> b = build;
                if (charset != null) {
                    b = org.mockito.Mockito.spy(build);
                    org.mockito.Mockito.doReturn(charset).when(b).getCharset();
                }
                return builder.perform(b, launcher[0], listener);
            }
        };
        p.getBuildersList().add(wrapper);
        ParametersDefinitionProperty params = p.getProperty(ParametersDefinitionProperty.class);
        FreeStyleBuild build = params == null
                ? p.scheduleBuild2(0).get()
                : p.scheduleBuild2(0, new ParametersAction(params.getParameterDefinitions().stream()
                        .map(d -> d.getDefaultParameterValue()).toList())).get();
        return new Run(build, launcher[0]);
    }
}
