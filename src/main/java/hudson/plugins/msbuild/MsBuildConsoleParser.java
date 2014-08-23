package hudson.plugins.msbuild;

import hudson.console.LineTransformationOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser to find the number of Warnings/Errors of MsBuild compilation
 *
 * @author Damien Finck
 */
public class MsBuildConsoleParser extends LineTransformationOutputStream {
    private final OutputStream out;
    private final Charset charset;

    private int numberOfWarnings = -1;
    private int numberOfErrors = -1;

    public MsBuildConsoleParser(OutputStream out, Charset charset) {
        this.out = out;
        this.charset = charset;
    }

    public int getNumberOfWarnings() {
        return numberOfWarnings;
    }

    public int getNumberOfErrors() {
        return numberOfErrors;
    }

    @Override
    protected void eol(byte[] b, int len) throws IOException {
        String line = charset.decode(ByteBuffer.wrap(b, 0, len)).toString();

        // trim off CR/LF from the end
        line = trimEOL(line);

        Pattern patternWarnings = Pattern.compile(".*\\d+\\sWarning\\(s\\).*");
        Pattern patternErrors = Pattern.compile(".*\\d+\\sError\\(s\\).*");

        Matcher mWarnings = patternWarnings.matcher(line);
        Matcher mErrors = patternErrors.matcher(line);

        if (mWarnings.matches()) { // Match the number of warnings
            String[] part = line.split(" ");
            try {
                numberOfWarnings = Integer.parseInt(part[4]);
            } catch (NumberFormatException e) {

            }
        } else if (mErrors.matches()) { // Match the number of errors
            String[] part = line.split(" ");
            try {
                numberOfErrors = Integer.parseInt(part[4]);
            } catch (NumberFormatException e) {

            }
        }

        // Write to output
        out.write(b, 0, len);
    }

    @Override
    public void close() throws IOException {
        super.close();
        out.close();
    }
}
