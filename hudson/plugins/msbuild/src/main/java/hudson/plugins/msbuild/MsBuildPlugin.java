package hudson.plugins.msbuild;

import hudson.Plugin;
import hudson.tasks.BuildStep;

/**
 * Entry point of a plugin.
 *
 * <p>
 * There must be one {@link Plugin} class in each plugin.
 * See javadoc of {@link Plugin} for more about what can be done on this class.
 *
 * @author kyle.sweeney@valtech.com
 * @plugin
 */
public class MsBuildPlugin extends Plugin {
    public void start() throws Exception {
        // plugins normally extend Hudson by providing custom implementations
        // of 'extension points'. In this case, we are adding the MsBuildBuilder 
    	// to the list of builders.
       BuildStep.BUILDERS.add(MsBuildBuilder.DESCRIPTOR);
    }
}
