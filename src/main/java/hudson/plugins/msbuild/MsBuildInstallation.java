package hudson.plugins.msbuild;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Gregory Boissinot
 */
public final class MsBuildInstallation {


    private final String name;
    private final String pathToMsBuild;

    @DataBoundConstructor
    public MsBuildInstallation(String name, String pathToMsBuild) {
        this.name = name;
        this.pathToMsBuild = pathToMsBuild;
    }

    public String getPathToMsBuild() {
        return pathToMsBuild;
    }

    public String getName() {
        return name;
    }
}
