package hudson.plugins.msbuild;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolInstallerDescriptor;
import hudson.util.ArgumentListBuilder;
import hudson.util.ListBoxModel;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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

    @Override
    public FilePath performInstallation(ToolInstallation tool, Node node, TaskListener log)
            throws IOException, InterruptedException {

        String givenArguments = getAdditionalArguments();

        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (!os.contains("win")) {
            log.getLogger().println("MSBuild is only available on Windows");
            throw new IOException("MSBuild is only available on Windows");
        }

        FilePath expected = preferredLocation(tool, node);
        FilePath installDir = expected
                .child("C:\\Program Files (x86)\\Microsoft Visual Studio\\" + selectedVersion + "\\BuildTools\\");
        if (givenArguments != null && StringUtils.contains(givenArguments, "--installPath")) {
            // Extract the existing installPath value
            String[] args = givenArguments.split(" ");
            for (int i = 0; i < args.length - 1; i++) {
                if (args[i].equals("--installPath")) {
                    installDir = new FilePath(node.getChannel(), args[i + 1]);
                    break;
                }
            }
        }
        FilePath msBuildExe = installDir.child("\\MSBuild\\Current\\Bin");

        expected.mkdirs();
        installDir.mkdirs();

        String url = ((DescriptorImpl) getDescriptor()).getUrlForVersion(selectedVersion);
        FilePath exePath = expected.child("vs_BuildTools.exe");

        for (int i = 0; i < 20; i++) {
            if (isInstallerRunning("setup.exe") || isInstallerRunning("vs_BuildTools.exe")) {
                log.getLogger().println("Visual Studio Build Tools waiting for the another Installer to finish...");
                Thread.sleep(30000);
            } else {
                break;
            }
        }

        if (!msBuildExe.exists()) {
            if (!StringUtils.contains(givenArguments, "--quiet")) {
                givenArguments += " --quiet";
            }
            if (!StringUtils.contains(givenArguments, "--wait")) {
                givenArguments += " --wait";
            }
            if (!StringUtils.contains(givenArguments, "--norestart")) {
                givenArguments += " --norestart";
            }
            if (getVsconfig() != null && !getVsconfig().isEmpty()) {
                FilePath vsConfigFile = expected.child(".vsconfig");
                vsConfigFile.write(getVsconfig(), "UTF-8");
                givenArguments += " --config " + vsConfigFile.getRemote();
            }
            log.getLogger().println("Downloading MSBuild version " + selectedVersion + " from " + url);
            exePath.copyFrom(new URL(url));
            exePath.chmod(0755);

            ArgumentListBuilder args = new ArgumentListBuilder();
            args.add("cmd.exe", "/C", "start", "/wait");
            args.add(exePath.getRemote());
            args.addTokenized(givenArguments);
            Launcher launcher = node.createLauncher(log);
            int result = launcher.launch().cmds(args).stdout(log).pwd(installDir).join();

            if (result != 0) {
                throw new IOException("Installation failed with exit code " + result);
            }
            Thread.sleep(30000);
            if (!msBuildExe.exists()) {
                throw new IOException("MSBuild executable not found in " + msBuildExe);
            }
            log.getLogger().println("MSBuild installed successfully");

        } else {
            // Try to Update to the latest version
            log.getLogger().println("MSBuild found at " + installDir);
            String updateArguments = "update --quiet --wait --norestart --installPath " + installDir;
            String modifyArguments = "modify --quiet --wait --norestart --installPath " + installDir;
            log.getLogger().println("Updating MSBuild version " + selectedVersion + " from " + url);
            if (getVsconfig() != null && !getVsconfig().isEmpty()) {
                FilePath vsConfigFile = expected.child(".vsconfig");
                vsConfigFile.write(getVsconfig(), "UTF-8");
                modifyArguments += " --config " + vsConfigFile.getRemote();
            }
            exePath.copyFrom(new URL(url));
            exePath.chmod(0755);

            ArgumentListBuilder updateArgs = new ArgumentListBuilder();
            updateArgs.add("cmd.exe", "/C", "start", "/wait");
            updateArgs.add(exePath.getRemote());
            updateArgs.addTokenized(updateArguments);
            Launcher launcher = node.createLauncher(log);
            int result = launcher.launch().cmds(updateArgs).stdout(log).pwd(installDir).join();

            if (result != 0) {
                throw new IOException("Update failed with exit code " + result);
            }

            ArgumentListBuilder modifyArgs = new ArgumentListBuilder();
            modifyArgs.add("cmd.exe", "/C", "start", "/wait");
            modifyArgs.add(exePath.getRemote());
            modifyArgs.addTokenized(modifyArguments);
            Launcher modifyLauncher = node.createLauncher(log);
            int modifyResult = modifyLauncher.launch().cmds(modifyArgs).stdout(log).pwd(installDir).join();

            if (modifyResult != 0) {
                throw new IOException("Modify failed with exit code " + modifyResult);
            }
        }

        return msBuildExe;
    }

    public static boolean isInstallerRunning(String processName) throws IOException {
        Process process = Runtime.getRuntime().exec("tasklist");

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            // Check each line of the output
            while ((line = reader.readLine()) != null) {
                if (line.contains(processName)) {
                    return true;
                }
            }
        } finally {
            process.destroy();
        }
        return false;
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

        public String getUrlForVersion(String version) {
            return VERSION_URL_MAP.get(version);
        }

        public ListBoxModel doFillSelectedVersionItems() {
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
