package hudson.plugins.msbuild;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.ArgumentListBuilder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.interceptor.RequirePOST;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MsBuildInstaller extends ToolInstaller {

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
        Boolean usesConfigFile = useConfigFile(getVsconfig(), expected);
        if (!needsModify(expected) && !needsUpdate(expected) && msBuildExe.exists()) {
            return msBuildBinPath;
        }
        buildToolsInstallPath.mkdirs();
        String url = DescriptorImpl.getUrlForVersion(selectedVersion);
        try {
            URI uri = new URI(url);
            log.getLogger().println("Downloading MSBuild version " + selectedVersion + " from " + url);
            downloadFile(uri, vs_BuildToolsExePath);
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URI: " + url);
        }

        waitUntilInstallerFinishes(log);
        vs_BuildToolsExePath.chmod(0755);

        if (!msBuildExe.exists()) {
            String[] requiredArgs = { "--quiet", "--wait", "--norestart" };
            givenArguments = ensureArguments(givenArguments, requiredArgs);
            if (usesConfigFile) {
                givenArguments += " --config " + expected.child(".vsconfig").getRemote();
            }

            log.getLogger().println("Installing MSBuild version " + selectedVersion + " from " + url);

            Boolean installResult = runVs_BuildToolsExe(vs_BuildToolsExePath, givenArguments, buildToolsInstallPath,
                    node, log, expected);

            if (!installResult) {
                throw new IOException("Installation failed with exit code " + installResult);
            }

            logLastUpdated(expected);

        } else if (needsModify(expected) || needsUpdate(expected)) {
            String updateArguments = "update --quiet --wait --norestart --installPath " + buildToolsInstallPath;
            String modifyArguments = "modify --quiet --wait --norestart --installPath " + buildToolsInstallPath;
            log.getLogger().println("Updating MSBuild version " + selectedVersion + " from " + url);

            if (usesConfigFile) {
                updateArguments += " --config " + expected.child(".vsconfig").getRemote();
                modifyArguments += " --config " + expected.child(".vsconfig").getRemote();
            }

            Boolean updateResult = runVs_BuildToolsExe(vs_BuildToolsExePath, updateArguments, buildToolsInstallPath,
                    node, log, expected);
            Boolean modifyResult = runVs_BuildToolsExe(vs_BuildToolsExePath, modifyArguments, buildToolsInstallPath,
                    node, log, expected);

            if (!updateResult || !modifyResult) {
                throw new IOException("Update failed with exit code " + updateResult);
            }
            logNeedsModify(expected, false);
            logLastUpdated(expected);
        }
        return msBuildBinPath;
    }

    /**
     * Check if the installer is running
     * 
     * @param processName String
     * 
     * @return boolean
     * 
     * @throws IOException
     */
    public static boolean isInstallerRunning(String processName) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder("jps", "-l");
        Process process = processBuilder.start();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().anyMatch(line -> line.contains(processName));
        } finally {
            process.destroy();
        }
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
        if (givenInstallPath != null) {
            return new FilePath(node.getChannel(), givenInstallPath);
        }
        return new FilePath(node.getChannel(),
                "C:\\Program Files (x86)\\Microsoft Visual Studio\\" + selectedVersion + "\\BuildTools\\");
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
        if (givenInstallPath != null && !givenInstallPath.isEmpty()) {
            return new FilePath(node.getChannel(), givenInstallPath).child("\\MSBuild\\Current\\Bin");
        }
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
            if (envVars != null && envVars.containsKey("OS") && envVars.get("OS").contains("Windows_NT")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Wait until the installer finishes
     * 
     * @param log TaskListener
     * 
     * @throws InterruptedException
     * 
     * @throws IOException
     */
    private static void waitUntilInstallerFinishes(TaskListener log) throws InterruptedException, IOException {
        for (int i = 0; i < 20; i++) {
            if (isInstallerRunning("setup.exe") || isInstallerRunning("vs_BuildTools.exe")) {
                log.getLogger().println("Visual Studio Build Tools waiting for the another Installer to finish...");
                Thread.sleep(30000);
            } else {
                break;
            }
        }
    }

    /**
     * Run the vs_BuildTools.exe
     * 
     * @param vs_BuildToolsExePath                       FilePath
     * 
     * @param givenArguments                             String
     * 
     * @param buildbuildToolsInstallPathToolsInstallPath FilePath
     * 
     * @param node                                       Node
     * 
     * @param log                                        TaskListener
     * 
     * @param expected                                   FilePath
     * 
     * @return boolean
     * 
     * @throws IOException
     * 
     * @throws InterruptedException
     */
    private static boolean runVs_BuildToolsExe(FilePath vs_BuildToolsExePath, String givenArguments,
            FilePath buildbuildToolsInstallPathToolsInstallPath, Node node, TaskListener log, FilePath expected)
            throws IOException, InterruptedException {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("cmd.exe", "/C", "start", "/wait");
        args.add(vs_BuildToolsExePath.getRemote());
        args.addTokenized(givenArguments);
        Launcher launcher = node.createLauncher(log);
        int result = launcher.launch().cmds(args).stdout(log).pwd(expected).join();

        if (result != 0) {
            throw new IOException("Installation failed with exit code " + result);
        }
        return true;
    }

    /**
     * Use the .vsconfig file to install the Visual Studio Build Tools
     * 
     * @param vsconfig String
     * 
     * @param expected FilePath
     * 
     * @return boolean
     * 
     * @throws IOException
     * 
     * @throws InterruptedException
     */
    public static boolean useConfigFile(String vsconfig, FilePath expected)
            throws IOException, InterruptedException {
        if (vsconfig != null && !vsconfig.isEmpty()) {
            FilePath vsConfigFile = expected.child(".vsconfig");
            if (vsConfigFile.exists()) {
                String existingContent = vsConfigFile.readToString();
                if (!existingContent.equals(vsconfig)) {
                    vsConfigFile.write(vsconfig, "UTF-8");
                    logNeedsModify(expected, true);
                } else {
                    logNeedsModify(expected, false);
                }
            } else {
                vsConfigFile.write(vsconfig, "UTF-8");
                logNeedsModify(expected, true);
            }
            return true;
        } else {
            expected.child(".vsconfig").delete();
            logNeedsModify(expected, true);
            return false;
        }
    }

    /**
     * Log the last updated time
     * 
     * @param expected FilePath
     * 
     * @throws IOException
     * 
     * @throws InterruptedException
     */
    private static void logLastUpdated(FilePath expected) throws IOException, InterruptedException {
        FilePath lastUpdated = expected.child("config.json");
        JSONObject json;
        if (lastUpdated.exists()) {
            String content = lastUpdated.readToString();
            json = (JSONObject) JSONSerializer.toJSON(content);
        } else {
            json = new JSONObject();
        }
        json.put("lastUpdated", System.currentTimeMillis() / 1000);
        lastUpdated.write(json.toString(), "UTF-8");
    }

    /**
     * Check if the installer needs to be updated
     * 
     * @param expected FilePath
     * 
     * @return boolean
     */
    public static boolean needsUpdate(FilePath expected) {
        try {
            FilePath needsModify = expected.child("config.json");
            if (needsModify.exists()) {
                JSONObject json = JSONObject.fromObject(needsModify.readToString());
                if (json.has("lastUpdated")) {
                    Long lastUpdatedTimestamp = json.getLong("lastUpdated");
                    long currentTimestamp = System.currentTimeMillis() / 1000;
                    long diffSeconds = currentTimestamp - lastUpdatedTimestamp;
                    long diffHours = diffSeconds / 3600;
                    return diffHours > 24;
                }
            }
        } catch (IOException | InterruptedException e) {
            return true;
        }
        return true;
    }

    /**
     * Log the needsModify file
     * 
     * @param expected FilePath
     * 
     * @throws IOException
     * 
     * @throws InterruptedException
     */
    private static void logNeedsModify(FilePath expected, Boolean updateValue)
            throws IOException, InterruptedException {
        FilePath needsModify = expected.child("config.json");
        JSONObject json;
        if (needsModify.exists()) {
            String content = needsModify.readToString();
            json = (JSONObject) JSONSerializer.toJSON(content);
        } else {
            json = new JSONObject();
        }
        json.put("needsModify", updateValue);
        needsModify.write(json.toString(), "UTF-8");
    }

    /**
     * Check if the installer needs to be modified
     * 
     * @param expected FilePath
     * 
     * @return boolean
     * 
     * @throws IOException
     * 
     * @throws InterruptedException
     */
    public static boolean needsModify(FilePath expected) throws IOException, InterruptedException {
        FilePath needsModify = expected.child("config.json");
        if (needsModify.exists()) {
            JSONObject json = JSONObject.fromObject(needsModify.readToString());
            if (json.has("needsModify")) {
                return json.getBoolean("needsModify");
            }
        }
        return false;
    }

    /**
     * Download a file from a URI to a target path
     * 
     * @param uri        URI
     * 
     * @param targetPath FilePath
     * 
     */
    public static void downloadFile(URI uri, FilePath targetPath)
            throws IOException, InterruptedException {
        URL url = uri.toURL();
        URLConnection connection = url.openConnection();
        try (InputStream in = connection.getInputStream();
                OutputStream out = targetPath.write()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
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
        StringBuilder sb = new StringBuilder(givenArguments);
        for (String arg : argsToAdd) {
            if (!StringUtils.contains(givenArguments, arg)) {
                sb.append(" ").append(arg);
            }
        }
        return sb.toString();
    }

    @Extension
    public static final class DescriptorImpl extends ToolInstallerDescriptor<MsBuildInstaller> {

        private static final Map<String, String> VERSION_URL_MAP = new HashMap<>();

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
            Collections.sort(versions, Collections.reverseOrder());
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
