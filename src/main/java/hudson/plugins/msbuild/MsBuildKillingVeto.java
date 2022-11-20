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

import hudson.Extension;
import hudson.util.ProcessKillingVeto;
import hudson.util.ProcessTreeRemoting.IOSProcess;

import java.util.List;
import java.util.Locale;

import org.apache.commons.io.FilenameUtils;
import org.jenkinsci.Symbol;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


/**
 * An extension that avoids mspdbsrv.exe being killed by Jenkins.
 * 
 * Requires a Jenkins version &gt;= 1.619. Will simply be ignored for older versions.
 * 
 * See JENKINS-9104
 * 
 * @author Daniel Weber &lt;daniel.weber.dev@gmail.com&gt;
 */
@Extension(optional = true) @Symbol("msbuildKillingVeto")
public class MsBuildKillingVeto extends ProcessKillingVeto {
    private static final VetoCause VETO_CAUSE = new VetoCause("MSBuild Plugin vetoes killing mspdbsrv.exe, see JENKINS-9104 for all the details");

    /**
    * 
    */
    @Override
    public VetoCause vetoProcessKilling(IOSProcess proc) {
        if (proc == null)
            return null;

        List<String> cmdLine = proc.getArguments();

        if (cmdLine.isEmpty())
            return null;

        String command = cmdLine.get(0);
        String exeName = FilenameUtils.getName(command);
        if (exeName.toLowerCase(Locale.getDefault()).equals("mspdbsrv.exe")) {
            return VETO_CAUSE;
        }
        return null;
    }
}
