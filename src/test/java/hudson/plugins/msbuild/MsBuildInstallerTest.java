package hudson.plugins.msbuild;

import com.sun.net.httpserver.HttpServer;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.AccessDeniedException3;
import hudson.util.ListBoxModel;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MsBuildInstallerTest {

    @TempDir
    Path tmp;

    private FilePath dir;
    private ByteArrayOutputStream logBytes;
    private TaskListener log;

    @BeforeEach
    void setUp() {
        dir = new FilePath(tmp.toFile());
        logBytes = new ByteArrayOutputStream();
        log = new StreamTaskListener(logBytes, StandardCharsets.UTF_8);
        MsBuildInstaller.installerRetryMillis = 0;
    }

    @AfterEach
    void tearDown() {
        MsBuildInstaller.installerRetryMillis = 30000;
        MsBuildInstaller.DescriptorImpl.VERSION_URL_MAP.remove("test");
    }

    // --- paths and arguments ------------------------------------------------

    @Test
    void buildToolsInstallPathWithGivenPath() {
        Node node = mock(Node.class);
        assertEquals("D:\\Custom\\BuildTools",
                MsBuildInstaller.buildToolsInstallPath(node, "2019", "D:\\Custom\\BuildTools").getRemote());
    }

    @Test
    void buildToolsInstallPathDefaultsToProgramFiles() {
        Node node = mock(Node.class);
        // FilePath normalizes away the trailing separator
        assertEquals("C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools",
                MsBuildInstaller.buildToolsInstallPath(node, "2019", null).getRemote());
        assertEquals(MsBuildInstaller.buildToolsInstallPath(node, "2019", null),
                MsBuildInstaller.buildToolsInstallPath(node, "2019", ""), "empty path falls back to the default");
    }

    @Test
    void msBuildBinPathIsBelowInstallPath() {
        Node node = mock(Node.class);
        FilePath bin = MsBuildInstaller.msBuildBinPath(node, "2019", tmp.toString());
        assertEquals(tmp.resolve("MSBuild").resolve("Current").resolve("Bin").toString(), bin.getRemote());
        assertEquals(MsBuildInstaller.buildToolsInstallPath(node, "2019", null).child("\\MSBuild\\Current\\Bin"),
                MsBuildInstaller.msBuildBinPath(node, "2019", ""), "empty path falls back to the default location");
    }

    @Test
    void vsBuildToolsExeIsInToolDirectory() {
        assertEquals(dir.child("vs_BuildTools.exe"), MsBuildInstaller.getVs_BuildToolsExePath(dir));
    }

    @Test
    void extractInstallPath() {
        assertNull(MsBuildInstaller.extractInstallPath(null));
        assertNull(MsBuildInstaller.extractInstallPath("--quiet --norestart"));
        assertEquals("D:\\MSBuild", MsBuildInstaller.extractInstallPath("--quiet --installPath D:\\MSBuild --wait"));
        assertEquals("C:\\Program Files\\MSBuild",
                MsBuildInstaller.extractInstallPath("--quiet --installPath \"C:\\Program Files\\MSBuild\""));
        assertEquals("C:\\Program Files\\MSBuild", MsBuildInstaller.extractInstallPath(
                "--installPath \"C:\\Program Files\\MSBuild\" --installPath D:\\MSBuild"), "first one wins");
    }

    @Test
    void stripTrailingBackslash() {
        assertEquals("C:\\a b", MsBuildInstaller.stripTrailingBackslash("C:\\a b\\"));
        assertEquals("C:\\a b", MsBuildInstaller.stripTrailingBackslash("C:\\a b"));
    }

    @Test
    void ensureArguments() {
        String[] required = {"--quiet", "--wait", "--norestart"};
        assertEquals("--quiet --wait --norestart", MsBuildInstaller.ensureArguments("--quiet", required));
        assertEquals("--quiet --wait --norestart", MsBuildInstaller.ensureArguments(null, required));
        assertEquals("--quiet --norestart --wait", MsBuildInstaller.ensureArguments("--quiet --norestart", required));
        assertEquals("", MsBuildInstaller.ensureArguments(null, new String[0]));
    }

    @Test
    void settersAndGetters() {
        MsBuildInstaller installer = new MsBuildInstaller("label");
        installer.setSelectedVersion("2022");
        installer.setAdditionalArguments("--quiet");
        installer.setVsconfig("{}");
        assertEquals("2022", installer.getSelectedVersion());
        assertEquals("--quiet", installer.getAdditionalArguments());
        assertEquals("{}", installer.getVsconfig());
        assertEquals("label", installer.getLabel());
    }

    // --- OS detection -------------------------------------------------------

    @Test
    void checkIfOsIsWindows() throws Exception {
        assertTrue(MsBuildInstaller.checkIfOsIsWindows(node(new EnvVars("OS", "Windows_NT"))));
        assertFalse(MsBuildInstaller.checkIfOsIsWindows(node(new EnvVars("OS", "Linux"))));
        assertFalse(MsBuildInstaller.checkIfOsIsWindows(node(new EnvVars())));
        assertFalse(MsBuildInstaller.checkIfOsIsWindows(mock(Node.class)), "offline node has no computer");
    }

    // --- performInstallation -----------------------------------------------

    /** Serves the bootstrapper over HTTP, as the download goes through the Jenkins proxy-aware HttpClient. */
    private HttpServer server;
    private volatile int downloadStatus = 200;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String serve(byte[] payload) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = downloadStatus == 200 ? payload : new byte[0];
            exchange.sendResponseHeaders(downloadStatus, body.length == 0 ? -1 : body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/vs.exe";
    }

    @Test
    void downloadFileCopiesContent() throws Exception {
        URI uri = URI.create(serve(new byte[] {1, 2, 3, 4}));

        MsBuildInstaller.downloadFile(uri, dir.child("target.bin"));

        assertArrayEquals(new byte[] {1, 2, 3, 4}, Files.readAllBytes(tmp.resolve("target.bin")));
    }

    private final class Install {
        final RecordingLauncher launcher = new RecordingLauncher(log);
        final Node node;
        final FilePath toolDir;
        // the default install path has spaces too: C:\Program Files (x86)\...
        final Path installDir = tmp.resolve("Build Tools");
        final FilePath msBuildExe;
        final MsBuildInstaller installer = new MsBuildInstaller(null);
        final MsBuildInstallation tool = new MsBuildInstallation("VS", "vs", Collections.emptyList(), null);

        Install(EnvVars env) throws Exception {
            node = node(env);
            when(node.getRootPath()).thenReturn(dir.child("agent"));
            when(node.createLauncher(log)).thenReturn(launcher);
            toolDir = dir.child("agent").child("tools").child("vs");
            msBuildExe = new FilePath(installDir.toFile()).child("MSBuild").child("Current").child("Bin")
                    .child("MSBuild.exe");
            MsBuildInstaller.DescriptorImpl.VERSION_URL_MAP.put("test", serve(new byte[] {42}));
            installer.setSelectedVersion("test");
            installer.setAdditionalArguments("--installPath \"" + installDir + "\"");
        }

        Install() throws Exception {
            this(new EnvVars("OS", "Windows_NT"));
        }

        FilePath run() throws Exception {
            return installer.performInstallation(tool, node, log);
        }

        List<String> installerCommands() {
            return launcher.commands.stream().map(c -> String.join(" ", c)).toList();
        }

        /** The bootstrapper arguments of the n-th launch, without {@code cmd.exe /C start "" /wait <exe>}. */
        List<String> arguments(int n) {
            return launcher.commands.get(n).subList(6, launcher.commands.get(n).size());
        }

        FilePath marker() {
            return toolDir.child(MsBuildInstaller.MARKER);
        }

        /** An existing install that last updated {@code hoursAgo} with {@code applied} as .vsconfig. */
        void installed(String applied, int hoursAgo) throws Exception {
            msBuildExe.write("", "UTF-8");
            marker().write(applied, "UTF-8");
            marker().touch(System.currentTimeMillis() - hoursAgo * 3600_000L);
        }

        boolean markerFresh() throws Exception {
            return System.currentTimeMillis() - marker().lastModified() < 60_000;
        }

        String log() {
            return logBytes.toString(StandardCharsets.UTF_8).replace("\r\n", "\n");
        }

        String serverUrl(String path) {
            return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + path;
        }
    }

    @Test
    void refusesNonWindowsNodes() throws Exception {
        Install install = new Install(new EnvVars("OS", "Linux"));

        assertThrows(UnsupportedOperationException.class, install::run);
    }

    @Test
    void freshInstall() throws Exception {
        Install install = new Install();
        install.installer.setVsconfig("{\"components\":[]}");

        FilePath bin = install.run();

        assertEquals(install.msBuildExe.getParent(), bin);
        assertArrayEquals(new byte[] {42}, Files.readAllBytes(tmp.resolve("agent/tools/vs/vs_BuildTools.exe")));
        assertEquals(1, install.launcher.commands.size(), install.installerCommands().toString());
        // empty window title, otherwise start treats a quoted exe path as the title
        assertEquals(List.of("cmd.exe", "/C", "start", "", "/wait",
                install.toolDir.child("vs_BuildTools.exe").getRemote()), install.launcher.commands.get(0).subList(0, 6));
        assertEquals(List.of("--installPath", install.installDir.toString(), "--quiet", "--wait", "--norestart",
                "--config", install.toolDir.child(".vsconfig").getRemote()), install.arguments(0));
        assertEquals("{\"components\":[]}", install.toolDir.child(".vsconfig").readToString());
        assertEquals("{\"components\":[]}", install.marker().readToString(), "records the applied config");
        assertTrue(install.markerFresh());
        assertTrue(install.log().contains("Installing MSBuild version test"));
    }

    @Test
    void freshInstallWithoutConfig() throws Exception {
        Install install = new Install();

        install.run();

        assertFalse(install.arguments(0).contains("--config"));
        assertEquals("", install.marker().readToString());
    }

    @Test
    void upToDateInstallIsReused() throws Exception {
        Install install = new Install();
        install.installer.setVsconfig("cfg");
        install.installed("cfg", 1);

        assertEquals(install.msBuildExe.getParent(), install.run());
        assertTrue(install.launcher.commands.isEmpty());
        assertFalse(install.toolDir.child("vs_BuildTools.exe").exists(), "nothing downloaded");
    }

    @Test
    void outdatedInstallIsUpdatedOnly() throws Exception {
        Install install = new Install();
        install.installer.setVsconfig("cfg");
        install.installed("cfg", 25);

        install.run();

        assertEquals(1, install.launcher.commands.size(), install.installerCommands().toString());
        assertEquals(List.of("update", "--quiet", "--wait", "--norestart", "--installPath",
                install.installDir.toString()), install.arguments(0), "--config is not documented for update");
        assertTrue(install.markerFresh());
    }

    @Test
    void installUpdatedWithinADayIsNotUpdated() throws Exception {
        Install install = new Install();
        install.installed("", 23);

        install.run();

        assertTrue(install.launcher.commands.isEmpty());
    }

    @Test
    void changedConfigModifiesRecentInstall() throws Exception {
        Install install = new Install();
        install.installed("old", 1);
        install.installer.setVsconfig("new");

        install.run();

        assertEquals(1, install.launcher.commands.size(), install.installerCommands().toString());
        assertEquals(List.of("modify", "--quiet", "--wait", "--norestart", "--installPath",
                install.installDir.toString(), "--config", install.toolDir.child(".vsconfig").getRemote()),
                install.arguments(0));
        assertEquals("new", install.marker().readToString());
    }

    @Test
    void outdatedInstallWithChangedConfigIsUpdatedAndModified() throws Exception {
        Install install = new Install();
        install.installed("old", 25);
        install.installer.setVsconfig("new");

        install.run();

        assertEquals(List.of("update", "modify"),
                List.of(install.arguments(0).get(0), install.arguments(1).get(0)));
    }

    @Test
    void failedModifyIsRetriedOnNextRun() throws Exception {
        Install install = new Install();
        install.installed("old", 1);
        install.installer.setVsconfig("new");
        install.launcher.exitCode = 1603;

        IOException e = assertThrows(IOException.class, install::run);
        assertEquals("Installation failed with exit code 1603", e.getMessage());
        assertEquals("old", install.marker().readToString(), "the change is still pending");

        install.launcher.exitCode = 0;
        install.run();

        assertEquals("modify", install.arguments(1).get(0));
        assertEquals("new", install.marker().readToString());
    }

    @Test
    void failedUpdateFailsAndIsRetriedOnNextRun() throws Exception {
        Install install = new Install();
        install.installed("", 25);
        install.launcher.exitCode = 1603;

        assertThrows(IOException.class, install::run);
        assertFalse(install.markerFresh());

        install.launcher.exitCode = 0;
        install.run();
        assertEquals(2, install.launcher.commands.size(), install.installerCommands().toString());
        assertTrue(install.markerFresh());
    }

    @Test
    void failedDownloadFailsUpdate() throws Exception {
        Install install = new Install();
        install.installed("", 25);
        downloadStatus = 503;

        IOException e = assertThrows(IOException.class, install::run);
        assertTrue(e.getMessage().contains("HTTP 503"), e.getMessage());
        assertTrue(install.launcher.commands.isEmpty());
    }

    @Test
    void logsResolvedDownloadUrlOnce() throws Exception {
        Install install = new Install();
        server.createContext("/short", exchange -> {
            exchange.getResponseHeaders().add("Location", "/vs.exe");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        String shortUrl = install.serverUrl("/short");
        MsBuildInstaller.DescriptorImpl.VERSION_URL_MAP.put("test", shortUrl);

        install.run();

        assertTrue(install.log().contains("Downloaded Visual Studio Build Tools bootstrapper from "
                + install.serverUrl("/vs.exe") + "\n"), install.log());
        assertFalse(install.log().contains(shortUrl), install.log());
    }

    @Test
    void failedDownloadFailsFreshInstall() throws Exception {
        Install install = new Install();
        downloadStatus = 503;

        IOException e = assertThrows(IOException.class, install::run);
        assertTrue(e.getMessage().contains("HTTP 503"), e.getMessage());
        assertFalse(install.marker().exists());
    }

    @Test
    void installWithoutMarkerIsUpdatedAndModifiedOnce() throws Exception {
        // installed by plugin 1.44 or older, which kept its state in config.json
        Install install = new Install();
        install.msBuildExe.write("", "UTF-8");
        install.toolDir.child("config.json").write("{\"needsModify\":false,\"lastUpdated\":1}", "UTF-8");
        install.installer.setVsconfig("cfg");

        install.run();
        assertFalse(install.toolDir.child("config.json").exists(), "the old state file is removed");
        install.run();

        assertEquals(2, install.launcher.commands.size(), install.installerCommands().toString());
        assertEquals("cfg", install.marker().readToString());
    }

    @Test
    void configJsonIsOnlyLookedForWithoutMarker() throws Exception {
        Install install = new Install();
        install.installed("", 1);
        install.toolDir.child("config.json").write("{}", "UTF-8");

        install.run();

        assertTrue(install.toolDir.child("config.json").exists(), "the marker exists, so there is nothing to migrate");
        assertTrue(install.launcher.commands.isEmpty());
    }

    @Test
    void clearedConfigRemovesVsconfigWithoutModify() throws Exception {
        Install install = new Install();
        install.installed("old", 1);
        install.toolDir.child(".vsconfig").write("old", "UTF-8");

        install.run();

        assertTrue(install.launcher.commands.isEmpty(), "modify without --config would not remove components");
        assertTrue(install.toolDir.child(".vsconfig").exists(), "up to date, nothing touched");
    }

    @Test
    void deletedBuildToolsAreReinstalled() throws Exception {
        Install install = new Install();
        install.installed("", 1);
        install.msBuildExe.delete();

        install.run();

        assertEquals("--installPath", install.arguments(0).get(0), "fresh install, not update");
    }

    @Test
    void failedInstallerRaisesIOException() throws Exception {
        Install install = new Install();
        install.launcher.exitCode = 1603;

        IOException e = assertThrows(IOException.class, install::run);
        assertEquals("Installation failed with exit code 1603", e.getMessage());
        assertFalse(install.marker().exists());
    }

    @Test
    void unknownVersionRaisesIOException() throws Exception {
        Install install = new Install();
        install.installer.setSelectedVersion("1999");

        IOException e = assertThrows(IOException.class, install::run);
        assertEquals("Unsupported MSBuild version: 1999", e.getMessage());
        assertFalse(Files.exists(install.installDir));
    }

    @Test
    void invalidUrlRaisesIOException() throws Exception {
        Install install = new Install();
        MsBuildInstaller.DescriptorImpl.VERSION_URL_MAP.put("test", "not a uri");

        IOException e = assertThrows(IOException.class, install::run);
        assertEquals("Invalid URI: not a uri", e.getMessage());
    }

    @Test
    void retriesWhileAnotherInstallationIsRunning() throws Exception {
        Install install = new Install();
        install.launcher.exitCodes.addAll(List.of(1618, 1001, 1003, 8006));

        install.run();

        assertEquals(5, install.launcher.commands.size());
        assertTrue(install.log().contains("Visual Studio installer is busy (exit code 1618), retrying in 0s..."));
    }

    @Test
    void givesUpWhenInstallerStaysBusy() throws Exception {
        Install install = new Install();
        install.launcher.exitCode = 1618;

        IOException e = assertThrows(IOException.class, install::run);
        assertEquals("Installation failed with exit code 1618", e.getMessage());
        assertEquals(20, install.launcher.commands.size());
    }

    @Test
    void rebootRequiredCountsAsSuccess() throws Exception {
        Install install = new Install();
        install.launcher.exitCode = 3010;

        install.run();

        assertTrue(install.marker().exists());
        assertTrue(install.log().contains("a reboot is required"));
    }

    // --- descriptor ---------------------------------------------------------

    @Test
    void urlForVersion() {
        assertEquals("https://aka.ms/vs/17/release/vs_buildtools.exe",
                MsBuildInstaller.DescriptorImpl.getUrlForVersion("2022"));
        assertEquals("https://aka.ms/vs/16/release/vs_buildtools.exe",
                MsBuildInstaller.DescriptorImpl.getUrlForVersion("2019"));
        assertNull(MsBuildInstaller.DescriptorImpl.getUrlForVersion("1999"));
    }

    @Test
    void descriptorBasics() {
        MsBuildInstaller.DescriptorImpl descriptor = new MsBuildInstaller.DescriptorImpl();
        assertEquals("Install from Microsoft", descriptor.getDisplayName());
        assertTrue(descriptor.isApplicable(MsBuildInstallation.class));
        assertFalse(descriptor.isApplicable(hudson.tools.ToolInstallation.class));
    }

    @Test
    @WithJenkins
    void fillSelectedVersionItemsRequiresAdmin(JenkinsRule r) {
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER).everywhere().to("admin")
                .grant(Jenkins.READ).everywhere().to("user"));
        MsBuildInstaller.DescriptorImpl descriptor =
                r.jenkins.getDescriptorByType(MsBuildInstaller.DescriptorImpl.class);

        try (ACLContext ignored = ACL.as(User.getById("admin", true))) {
            ListBoxModel items = descriptor.doFillSelectedVersionItems();
            assertEquals(List.of("2022", "2019"), items.stream().map(o -> o.value).toList());
            assertEquals("Build Tools 2022", items.get(0).name);
        }
        try (ACLContext ignored = ACL.as(User.getById("user", true))) {
            assertThrows(AccessDeniedException3.class, descriptor::doFillSelectedVersionItems);
        }
    }

    // --- helpers ------------------------------------------------------------

    private static Node node(EnvVars env) throws Exception {
        Node node = mock(Node.class);
        Computer computer = mock(Computer.class);
        when(node.toComputer()).thenReturn(computer);
        when(computer.getEnvironment()).thenReturn(env);
        return node;
    }
}
