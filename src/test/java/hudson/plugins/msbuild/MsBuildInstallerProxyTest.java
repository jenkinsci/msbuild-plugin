package hudson.plugins.msbuild;

import com.sun.net.httpserver.HttpServer;
import hudson.FilePath;
import hudson.ProxyConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithJenkins
class MsBuildInstallerProxyTest {

    private static final URI UNRESOLVABLE_URI = URI.create("http://msbuild-proxy-test.invalid/vs_buildtools.exe");

    @TempDir
    File tempDir;

    private JenkinsRule jenkins;
    private HttpServer fakeProxy;
    private final AtomicReference<String> hostRequestedFromProxy = new AtomicReference<>();
    private volatile int proxyResponseStatus = 200;
    private volatile String proxyResponseBody = "installer-content";

    @BeforeEach
    void startFakeProxyAndConfigureJenkins(JenkinsRule jenkins) throws IOException {
        this.jenkins = jenkins;

        fakeProxy = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        fakeProxy.createContext("/", exchange -> {
            hostRequestedFromProxy.set(exchange.getRequestHeaders().getFirst("Host"));
            byte[] body = proxyResponseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(proxyResponseStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        fakeProxy.start();

        jenkins.jenkins.proxy = new ProxyConfiguration("localhost", fakeProxy.getAddress().getPort());
    }

    @AfterEach
    void stopFakeProxy() {
        fakeProxy.stop(0);
    }

    @Test
    void downloadIsRoutedThroughJenkinsProxy() throws Exception {
        File target = new File(tempDir, "vs_buildtools.exe");

        MsBuildInstaller.downloadFile(UNRESOLVABLE_URI, new FilePath(target));

        assertEquals(UNRESOLVABLE_URI.getHost(), hostRequestedFromProxy.get(),
                "the request should have been sent to the Jenkins proxy");
        assertEquals("installer-content", Files.readString(target.toPath()),
                "the response of the proxy should have been written to the target file");
    }

    @Test
    void downloadFailsOnHttpErrorAndDoesNotWriteTargetFile() {
        proxyResponseStatus = 404;
        proxyResponseBody = "not found";
        File target = new File(tempDir, "vs_buildtools.exe");

        IOException e = assertThrows(IOException.class,
                () -> MsBuildInstaller.downloadFile(UNRESOLVABLE_URI, new FilePath(target)));

        assertTrue(e.getMessage().contains("404"), "the message should contain the HTTP status: " + e.getMessage());
        assertFalse(target.exists(), "an error page must not be written as installer");
    }
}
