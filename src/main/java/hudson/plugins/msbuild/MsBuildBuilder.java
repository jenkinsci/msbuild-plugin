package hudson.plugins.msbuild;

import hudson.Launcher;
import hudson.Util;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Project;
import hudson.tasks.Builder;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.util.Map;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Sample {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link MsBuildBuilder} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #name})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(Build, Launcher, BuildListener)} method
 * will be invoked. 
 * 
 * @author kyle.sweeney@valtech.com
 *
 */
public class MsBuildBuilder extends Builder {

	private final String msBuildFile;
	private final String cmdLineArgs;
	
	/**
	 * When this builder is created in the project configuration step,
	 * the builder object will be created from the strings below.
	 * @param msBuildFile	The name/location of the msbuild file
	 * @param targets Whitespace separated list of command line arguments
	 */
    @DataBoundConstructor
    public MsBuildBuilder(String msBuildFile,String cmdLineArgs) {
    	super();
    	if(msBuildFile==null || msBuildFile.trim().length()==0)
    		this.msBuildFile = "";
    	else
    		this.msBuildFile = msBuildFile;
    	if(cmdLineArgs==null || cmdLineArgs.trim().length()==0)
    		this.cmdLineArgs = "";
    	else
    		this.cmdLineArgs = cmdLineArgs;
    }

    /**
     * We'll use these from the <tt>config.jelly</tt>.
     */
    public String getCmdLineArgs(){
        return cmdLineArgs;
    }
    public String getMsBuildFile(){
    	return msBuildFile;
    }

    public boolean perform(Build<?,?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
        Project proj = build.getProject();
        ArgumentListBuilder args = new ArgumentListBuilder();

        //Get the path to the nant installation
    	listener.getLogger().println("Path To MSBuild.exe: " + DESCRIPTOR.getPathToMsBuild());
    	String msbuildExe = DESCRIPTOR.getPathToMsBuild();
        
    	//Create a new commandline target with the name of the executable as the first
    	//paramater
        String execName=msbuildExe;
        args.add(execName);
        
        //Remove all tabs, carriage returns, and newlines and replace them with
        //whitespaces, so that we can add them as parameters to the executable
        String normalizedTarget = cmdLineArgs.replaceAll("[\t\r\n]+"," ");
        if(normalizedTarget.trim().length()>0)
        	args.addTokenized(normalizedTarget);
        
        //If a msbuild file is specified, then add it as an argument, otherwise
        //msbuild will search for any file that ends in .proj or .sln
        if(msBuildFile != null && msBuildFile.trim().length() > 0){
        	args.add(msBuildFile);
        }
        
        //According to the Ant builder source code, in order to launch a program 
        //from the command line in windows, we must wrap it into cmd.exe.  This 
        //way the return code can be used to determine whether or not the build failed.
        if(!launcher.isUnix()) {
            args.prepend("cmd.exe","/C");
            args.add("&&","exit","%%ERRORLEVEL%%");
        }

        //Try to execute the command
    	listener.getLogger().println("Executing command: "+args.toString());
    	Map<String,String> env = build.getEnvVars();
        try {
            int r = launcher.launch(args.toCommandArray(),env,listener.getLogger(),proj.getModuleRoot()).join();
            return r==0;
        } catch (IOException e) {
            Util.displayIOException(e,listener);
            e.printStackTrace( listener.fatalError("command execution failed") );
            return false;
        }
    }

    public Descriptor<Builder> getDescriptor() {
        // see Descriptor javadoc for more about what a descriptor is.
        return DESCRIPTOR;
    }

    /**
     * Descriptor should be singleton.
     */
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

    /**
     * Descriptor for {@link MsBuildBuilder}. Used as a singleton.
     * The class is marked as public so that it can be accessed from views.
     */
    public static final class DescriptorImpl extends Descriptor<Builder> {
        /**
         * To persist global configuration information,
         * simply store it in a field and call save().
         *
         * <p>
         * If you don't want fields to be persisted, use <tt>transient</tt>.
         */
    	public static String PARAMETERNAME_PATH_TO_MSBUILD = "pathToMsBuild";
    	private static String DEFAULT_PATH_TO_MSBUILD = "msbuild.exe";
    	
        private String pathToMsBuild;

        DescriptorImpl() {
        	super(MsBuildBuilder.class);
            load();
            if(pathToMsBuild==null || pathToMsBuild.length()==0){
            	pathToMsBuild = DEFAULT_PATH_TO_MSBUILD;
            	save();
            }
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Build a Visual Studio project or solution using MSBuild.";
        }
        
        @Override
        public boolean configure(StaplerRequest req) throws FormException{
        	// to persist global configuration information,
            // set that to properties and call save().
            pathToMsBuild = req.getParameter("descriptor."+PARAMETERNAME_PATH_TO_MSBUILD);
            if(pathToMsBuild == null || pathToMsBuild.length()==0){
            	pathToMsBuild = DEFAULT_PATH_TO_MSBUILD;
            }
            save();
            return true;
        }

        /**
         * This method returns the path to the msbuild.exe file for executing msbuild
         */
        public String getPathToMsBuild() {
            return pathToMsBuild;
        }

      
        
        @Override
		public Builder newInstance(StaplerRequest arg0, JSONObject arg1) throws FormException {
        	String buildFile= arg1.getString("msBuildFile");
        	String cmdLineArg= arg1.getString("cmdLineArgs");
        	MsBuildBuilder builder = new MsBuildBuilder(buildFile,cmdLineArg);
			
			return builder;
		}

    }
}
