package com.pubgm.utils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class Shell {

    // ✅ Root check
    public static boolean rootAccess() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("id\n");
            os.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    // ✅ Normal command
    public static Result cmd(String command) {
        return exec(command, false);
    }

    // ✅ Root command
    public static Result su(String command) {
        return exec(command, true);
    }

    private static Result exec(String command, boolean root) {
        StringBuilder output = new StringBuilder();
        try {
            Process process;
            if (root) {
                process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            } else {
                process = Runtime.getRuntime().exec(command);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            return new Result(process.exitValue(), output.toString());
        } catch (Exception e) {
            return new Result(-1, e.toString());
        }
    }

    public static class Result {
        public final int code;
        public final String out;

        public Result(int code, String out) {
            this.code = code;
            this.out = out;
        }

        public boolean isSuccess() {
            return code == 0;
        }
    }
}