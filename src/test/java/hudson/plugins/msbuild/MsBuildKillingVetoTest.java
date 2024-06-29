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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import hudson.EnvVars;
import hudson.util.ProcessKillingVeto.VetoCause;
import hudson.util.ProcessTree.ProcessCallable;
import hudson.util.ProcessTreeRemoting.IOSProcess;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;

public class MsBuildKillingVetoTest {

    private MsBuildKillingVeto testee;

    @Before
    public void setUp() {
        testee = new MsBuildKillingVeto();
    }

    @Test
    public void testProcessIsNull() {
        assertNull("Should return null if process is null", testee.vetoProcessKilling(null));
    }

    @Test
    public void testCommandLineIsEmpty() {
        IOSProcess emptyArgsProcess = mockProcess();
        assertNull("Should return null if command line arguments are empty", testee.vetoProcessKilling(emptyArgsProcess));
    }

    @Test
    public void testSparesMsPDBSrv() {
        VetoCause veto = testee.vetoProcessKilling(mockProcess("C:\\Program Files (x86)\\Microsoft Visual Studio\\bin\\mspdbsrv.exe", "something", "else"));
        assertNotNull(veto);
        assertEquals("MSBuild Plugin vetoes killing mspdbsrv.exe, see JENKINS-9104 for all the details", veto.getMessage());
    }

    @Test
    public void testIgnoresCase() {
        VetoCause veto = testee.vetoProcessKilling(mockProcess("C:\\Program Files (x86)\\Microsoft Visual Studio\\bin\\MsPdbSrv.exe", "something", "else"));
        assertNotNull(veto);
        assertEquals("MSBuild Plugin vetoes killing mspdbsrv.exe, see JENKINS-9104 for all the details", veto.getMessage());
    }

    @Test
    public void testPathDoesNotMatter() {
        VetoCause veto = testee.vetoProcessKilling(mockProcess("D:/Tools/mspdbsrv.exe"));
        assertNotNull(veto);
        assertEquals("MSBuild Plugin vetoes killing mspdbsrv.exe, see JENKINS-9104 for all the details", veto.getMessage());
    }

    @Test
    public void testLeavesOthersAlone() {
        assertNull(testee.vetoProcessKilling(mockProcess("D:/Tools/somethingElse.exe")));
        assertNull(testee.vetoProcessKilling(mockProcess("C:\\Program Files (x86)\\Microsoft Visual Studio\\bin\\cl.exe")));
        assertNull(testee.vetoProcessKilling(mockProcess("C:\\Program Files (x86)\\Microsoft Visual Studio\\bin\\link.exe")));
    }

    private IOSProcess mockProcess(final String... cmdLine) {
        return new IOSProcess() {
            @Override
            public void killRecursively() throws InterruptedException {
            }

            @Override
            public void kill() throws InterruptedException {
            }

            @Override
            public int getPid() {
                return 0;
            }

            @Override
            public IOSProcess getParent() {
                return null;
            }

            @Override
            public EnvVars getEnvironmentVariables() {
                return null;
            }

            @Override
            public List<String> getArguments() {
                return Lists.newArrayList(cmdLine);
            }

            @Override
            public <T> T act(ProcessCallable<T> arg0) throws IOException, InterruptedException {
                return null;
            }
        };
    }
}
