package rs.simovic.hyperdebloat;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class DebloatService extends IDebloatService.Stub {

    public DebloatService() {
    }

    public DebloatService(Context context) {
    }

    @Override
    public String exec(String command) {
        StringBuilder out = new StringBuilder();
        try {
            Process process = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }

            int code = process.waitFor();
            out.append("__EXIT__=").append(code);
        } catch (Throwable t) {
            out.append("ERROR: ").append(t.getClass().getSimpleName())
                    .append(": ").append(t.getMessage())
                    .append("\n__EXIT__=255");
        }
        return out.toString();
    }

    @Override
    public void destroy() {
        System.exit(0);
    }
}
