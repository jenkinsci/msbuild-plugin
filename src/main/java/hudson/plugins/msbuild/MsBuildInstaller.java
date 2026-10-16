package hudson.plugins.msbuild;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.ArgumentListBuilder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MsBuildInstaller extends ToolInstaller {

    // Exit codes of the Visual Studio installer, see
    // https://learn.microsoft.com/en-us/visualstudio/install/use-command-line-parameters-to-install-visual-studio#error-codes
    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_REBOOT_REQUIRED = 3010;
    /** Installer running (1001), Visual Studio in use (1003), another installation (1618), VS processes (8006). */
    private static final Set<Integer> EXIT_BUSY = Set.of(1001, 1003, 1618, 8006);
    private static final int MAX_ATTEMPTS = 20;

    /**
     * Written to the tool directory after a successful install, update or modify. It holds the applied
     * {@code .vsconfig} and its timestamp is the time of the last update.
     */
    static final String MARKER = ".installed";
    private static final long UPDATE_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(24);

    /** Delay before retrying while another installation is running; tests set it to 0. */
    static long installerRetryMillis = 30000;

    private String selectedVersion;
    private String additionalArguments;
    private String vsconfig;

    @DataBoundConstructor
    public MsBuildInstaller(String label) {
        super(label);
    }

    public String getSelectedVersion() {
        return selectedVersion;
    }

    @DataBoundSetter
    public void setSelectedVersion(String selectedVersion) {
        this.selectedVersion = selectedVersion;
    }

    public String getAdditionalArguments() {
        return additionalArguments;
    }

    @DataBoundSetter
    public void setAdditionalArguments(String additionalArguments) {
        this.additionalArguments = additionalArguments;
    }

    @DataBoundSetter
    public void setVsconfig(String vsconfig) {
        this.vsconfig = vsconfig;
    }

    public String getVsconfig() {
        return vsconfig;
    }

    /**
     * Perform the installation of the Visual Studio Build Tools
     *
     * @param tool ToolInstallation
     *
     * @param node Node
     *
     * @param log  TaskListener
     *
     * @return FilePath
     *
     * @throws IOException
     *
     * @throws InterruptedException
     */
    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log)
            throws IOException, InterruptedException {

        if (!checkIfOsIsWindows(node)) {
            throw new UnsupportedOperationException("MSBuild is only available on Windows");
        }

        String givenArguments = getAdditionalArguments();
        FilePath expected = preferredLocation(tool, node);
        FilePath vs_BuildToolsExePath = getVs_BuildToolsExePath(expected);
        FilePath buildToolsInstallPath = buildToolsInstallPath(node, selectedVersion,
                extractInstallPath(givenArguments));
        FilePath msBuildBinPath = msBuildBinPath(node, selectedVersion, extractInstallPath(givenArguments));
        FilePath msBuildExe = msBuildBinPath.child("MSBuild.exe");
        FilePath marker = expected.child(MARKER);
        String config = Util.fixEmpty(getVsconfig());
        boolean installed = msBuildExe.exists();
        String applied = marker.exists() ? marker.readToString() : null;
        if (applied == null) {
            // state file of plugin versions up to 1.44, replaced by the marker
            expected.child("config.json").delete();
        }
        boolean configChanged = config != null && !config.equals(applied);
        boolean outdated = applied == null
                || System.currentTimeMillis() - marker.lastModified() > UPDATE_INTERVAL_MILLIS;
        if (installed && !configChanged && !outdated) {
            return msBuildBinPath;
        }
        String url = DescriptorImpl.getUrlForVersion(selectedVersion);
        if (url == null) {
            throw new IOException("Unsupported MSBuild version: " + selectedVersion);
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI: " + url);
        }
        buildToolsInstallPath.mkdirs();

        FilePath vsConfigFile = expected.child(".vsconfig");
        if (config != null) {
            vsConfigFile.write(config, "UTF-8");
        } else {
            vsConfigFile.delete();
        }
        // paths are added as single arguments so spaces (e.g. "Program Files (x86)") survive
        String installPath = stripTrailingBackslash(buildToolsInstallPath.getRemote());
        String configPath = vsConfigFile.getRemote();

        if (!installed) {
            String[] requiredArgs = { "--quiet", "--wait", "--norestart" };
            ArgumentListBuilder installArguments = new ArgumentListBuilder()
                    .addTokenized(ensureArguments(givenArguments, requiredArgs));
            if (config != null) {
                installArguments.add("--config", configPath);
            }
            download(uri, vs_BuildToolsExePath, log);
            log.getLogger().println("Installing MSBuild version " + selectedVersion);
            runVs_BuildToolsExe(vs_BuildToolsExePath, installArguments, node, log, expected);
        } else {
            // a failure fails the build and leaves the marker untouched, so the next run retries
            download(uri, vs_BuildToolsExePath, log);
            if (outdated) {
                log.getLogger().println("Updating MSBuild version " + selectedVersion);
                runVs_BuildToolsExe(vs_BuildToolsExePath, new ArgumentListBuilder("update", "--quiet",
                        "--wait", "--norestart", "--installPath", installPath), node, log, expected);
            }
            if (configChanged) {
                // --config is only documented for install and modify
                log.getLogger().println("Applying changed .vsconfig to MSBuild version " + selectedVersion);
                runVs_BuildToolsExe(vs_BuildToolsExePath, new ArgumentListBuilder("modify", "--quiet",
                        "--wait", "--norestart", "--installPath", installPath, "--config", configPath),
                        node, log, expected);
            }
        }
        marker.write(config == null ? "" : config, "UTF-8");
        // set by the controller's clock, which is also the one it is compared with
        marker.touch(System.currentTimeMillis());
        return msBuildBinPath;
    }

    private static void download(URI uri, FilePath target, TaskListener log)
            throws IOException, InterruptedException {
        // aka.ms only redirects, log where the bootstrapper really came from
        URI resolved = downloadFile(uri, target);
        log.getLogger().println("Downloaded Visual Studio Build Tools bootstrapper from " + resolved);
        target.chmod(0755);
    }

    /** A trailing backslash before the closing quote would escape it: {@code "C:\\a b\\"}. */
    static String stripTrailingBackslash(String path) {
        return path.endsWith("\\") ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * Get the path to the Build Tools Install Path
     *
     * @param node             Node
     *
     * @param selectedVersion  String
     *
     * @param givenInstallPath String
     *
     * @return FilePath
     */
    public static FilePath buildToolsInstallPath(Node node, String selectedVersion, String givenInstallPath) {
        return new FilePath(node.getChannel(),
                Objects.requireNonNullElseGet(Util.fixEmpty(givenInstallPath),
                        () -> "C:\\Program Files (x86)\\Microsoft Visual Studio\\" + selectedVersion + "\\BuildTools\\"));
    }

    /**
     * Get the path to the MSBuild Bin folder
     *
     * @param node             Node
     *
     * @param selectedVersion  String
     *
     * @param givenInstallPath String
     *
     * @return FilePath
     */
    public static FilePath msBuildBinPath(Node node, String selectedVersion, String givenInstallPath) {
        return buildToolsInstallPath(node, selectedVersion, givenInstallPath).child("\\MSBuild\\Current\\Bin");
    }

    /**
     * Get the path to the vs_BuildTools.exe
     *
     * @param expected FilePath
     *
     * @return FilePath
     */
    public static FilePath getVs_BuildToolsExePath(FilePath expected) {
        return expected.child("vs_BuildTools.exe");
    }

    /**
     * Extract the installPath from the given arguments
     *
     * @param givenArguments String
     *
     * @return String
     */
    public static String extractInstallPath(String givenArguments) {
        if (givenArguments != null) {
            // Use a regex to find the --installPath followed by a quoted string or a single word
            Pattern pattern = Pattern.compile("--installPath\\s+\"([^\"]*)\"|--installPath\\s+(\\S+)");
            Matcher matcher = pattern.matcher(givenArguments);

            if (matcher.find()) {
                // Check if the path is captured from a quoted string or a non-quoted string
                return matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            }
        }
        return null;
    }

    /**
     * Check if the OS is Windows.
     *
     * @param node Node
     *
     * @return boolean
     *
     * @throws IOException
     *
     * @throws InterruptedException
     */
    public static boolean checkIfOsIsWindows(Node node) throws IOException, InterruptedException {
        Computer computer = node.toComputer();
        if (computer != null) {
            EnvVars envVars = computer.getEnvironment();
            // First check the 'OS' environment variable which is set to 'Windows_NT' on
            // Windows systems
            return envVars != null && envVars.containsKey("OS") && envVars.get("OS").contains("Windows_NT");
        }
        return false;
    }

    /**
     * Run the vs_BuildTools.exe
     *
     * @param vs_BuildToolsExePath                       FilePath
     *
     * @param givenArguments                             ArgumentListBuilder
     *
     * @param node                                       Node
     *
     * @param log                                        TaskListener
     *
     * @param expected                                   FilePath
     *
     * @throws IOException
     *
     * @throws InterruptedException
     */
    private static void runVs_BuildToolsExe(FilePath vs_BuildToolsExePath, ArgumentListBuilder givenArguments,
            Node node, TaskListener log, FilePath expected)
            throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("cmd.exe", "/C", "start", "", "/wait");
        args.add(vs_BuildToolsExePath.getRemote());
        args.add(givenArguments.toList());
        Launcher launcher = node.createLauncher(log);
        for (int attempt = 1;; attempt++) {
            int result = launcher.launch().cmds(args).stdout(log).pwd(expected).join();
            if (result == EXIT_SUCCESS) {
                return;
            }
            if (result == EXIT_REBOOT_REQUIRED) {
                log.getLogger().println("Visual Studio Build Tools installed; a reboot is required to complete it");
                return;
            }
            if (EXIT_BUSY.contains(result) && attempt < MAX_ATTEMPTS) {
                log.getLogger().println("Visual Studio installer is busy (exit code " + result + "), retrying in "
                        + installerRetryMillis / 1000 + "s...");
                Thread.sleep(installerRetryMillis);
                continue;
            }
            throw new IOException("Installation failed with exit code " + result);
        }
    }

    /**
     * Download a file from a URI to a target path
     *
     * @param uri        URI
     *
     * @param targetPath FilePath
     *
     * @return the URI the file was downloaded from after redirects
     *
     */
    public static URI downloadFile(URI uri, FilePath targetPath)
            throws IOException, InterruptedException {
        HttpRequest request = ProxyConfiguration.newHttpRequestBuilder(uri).GET().build();
        try (HttpClient client = ProxyConfiguration.newHttpClient()) {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("Failed to download " + uri + ": HTTP " + response.statusCode());
                }
                try (OutputStream out = targetPath.write()) {
                    in.transferTo(out);
                }
                // the URI after redirects
                return response.uri();
            }
        }
    }

    /**
     * Ensures that the specified arguments are present in the given argument
     * string.
     * If an argument is missing, it is appended to the string.
     *
     * @param givenArguments the initial argument string
     * @param argsToAdd      array of arguments to ensure presence
     * @return the modified argument string with all specified arguments included
     */
    public static String ensureArguments(String givenArguments, String[] argsToAdd) {
        List<String> args = new ArrayList<>();
        if (givenArguments != null) {
            args.add(givenArguments);
        }

        for (String arg : argsToAdd) {
            if (givenArguments == null || !givenArguments.contains(arg)) {
                args.add(arg);
            }
        }

        return String.join(" ", args);
    }

    @Extension
    public static final class DescriptorImpl extends ToolInstallerDescriptor<MsBuildInstaller> {

        static final Map<String, String> VERSION_URL_MAP = new HashMap<>();

        static {
            VERSION_URL_MAP.put("2022",
                    "https://aka.ms/vs/17/release/vs_buildtools.exe");
            VERSION_URL_MAP.put("2019",
                    "https://aka.ms/vs/16/release/vs_buildtools.exe");
        }

        public static String getUrlForVersion(String version) {
            return VERSION_URL_MAP.get(version);
        }

        @RequirePOST
        public ListBoxModel doFillSelectedVersionItems() {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            ListBoxModel items = new ListBoxModel();
            List<String> versions = new ArrayList<>(VERSION_URL_MAP.keySet());
            versions.sort(Collections.reverseOrder());
            for (String version : versions) {
                items.add("Build Tools " + version, version);
            }
            return items;
        }

        @Override
        public String getDisplayName() {
            return "Install from Microsoft";
        }

        @Override
        public boolean isApplicable(Class<? extends ToolInstallation> toolType) {
            return MsBuildInstallation.class.isAssignableFrom(toolType);
        }
    }
}
