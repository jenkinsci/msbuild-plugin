package hudson.plugins.msbuild;

import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.TaskListener;
import org.jvnet.hudson.test.FakeLauncher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Launcher that records commands instead of running them and pretends to be on Windows.
 */
class RecordingLauncher extends Launcher.LocalLauncher {

    final List<List<String>> commands = new ArrayList<>();
    final List<FilePath> dirs = new ArrayList<>();
    /** Exit codes of the next launches, in order; {@link #exitCode} once exhausted. */
    final Deque<Integer> exitCodes = new ArrayDeque<>();
    String output = "";
    int exitCode = 0;
    boolean unix = false;
    IOException failure;

    RecordingLauncher(TaskListener listener) {
        super(listener);
    }

    @Override
    public boolean isUnix() {
        return unix;
    }

    @Override
    public Proc launch(ProcStarter starter) throws IOException {
        if (failure != null) {
            throw failure;
        }
        commands.add(starter.cmds());
        dirs.add(starter.pwd());
        if (starter.stdout() != null) {
            starter.stdout().write(output.getBytes(StandardCharsets.UTF_8));
            starter.stdout().flush();
        }
        return new FakeLauncher.FinishedProc(exitCodes.isEmpty() ? exitCode : exitCodes.poll());
    }

    List<String> lastCommand() {
        return commands.get(commands.size() - 1);
    }
}
