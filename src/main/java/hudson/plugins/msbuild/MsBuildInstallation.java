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

import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.EnvironmentSpecific;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest2;

import java.io.Serial;
import java.util.Collections;
import java.util.List;
import java.io.IOException;

/**
 * @author Gregory Boissinot
 */
public final class MsBuildInstallation extends ToolInstallation
        implements NodeSpecific<MsBuildInstallation>, EnvironmentSpecific<MsBuildInstallation> {

    @Serial
    private static final long serialVersionUID = 1L;
    private final String defaultArgs;

    @DataBoundConstructor
    public MsBuildInstallation(String name, String home, List<? extends ToolProperty<?>> properties, String defaultArgs) {
        super(name, home, properties);
        this.defaultArgs = Util.fixEmptyAndTrim(defaultArgs);
    }

    public String getDefaultArgs() {
        return defaultArgs;
    }

    public void buildEnvVars(EnvVars env) {
        String msBuildBinPath = getHome();
        String msDefaultArgs = getDefaultArgs();
        env.put("MSBUILD_HOME", msBuildBinPath);
        env.put("MSBUILD_ARGS", msDefaultArgs != null ? msDefaultArgs : "");
        env.put("PATH+MSBUILD", msBuildBinPath);
    }

    @Override
    public MsBuildInstallation forNode(Node node, TaskListener log) throws IOException, InterruptedException {
        return new MsBuildInstallation(getName(), translateFor(node, log), getProperties().toList(), defaultArgs);
    }

    @Override
    public MsBuildInstallation forEnvironment(EnvVars environment) {
        return new MsBuildInstallation(getName(), environment.expand(getHome()), getProperties().toList(), defaultArgs);
    }

    @Extension
    @Symbol("msbuild")
    public static class DescriptorImpl extends ToolDescriptor<MsBuildInstallation> {

        @Override
        public String getDisplayName() {
            return "MSBuild";
        }

        @Override
        public MsBuildInstallation[] getInstallations() {
            return getDescriptor().getInstallations();
        }

        @Override
        public void setInstallations(MsBuildInstallation... installations) {
            getDescriptor().setInstallations(installations);
        }

        private MsBuildBuilder.DescriptorImpl getDescriptor() {
            Jenkins jenkins = Jenkins.get();
            return jenkins.getDescriptorByType(MsBuildBuilder.DescriptorImpl.class);
        }

        @Override
        public List<? extends ToolInstaller> getDefaultInstallers() {
            return Collections.singletonList(new MsBuildInstaller(null));
        }

        @Override
        public MsBuildInstallation newInstance(StaplerRequest2 req, JSONObject formData) throws FormException {
            return (MsBuildInstallation)super.newInstance(req, formData);
        }
    }

}
