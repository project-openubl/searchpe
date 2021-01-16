package io.github.project.openubl.searchpe.utils;

public class DataHelper {

    public static String[] readLine(String line, int size) {
        String[] result = new String[size];

        String[] split = line.split("\\|");
        for (int i = 0; i < result.length; i++) {
            if (i < split.length) {
                String value = split[i].trim();
                if (value.equals("-") || value.isEmpty()) {
                    split[i] = null;
                }

                result[i] = split[i];
            } else {
                result[i] = null;
            }
        }

        return split;
    }

}
