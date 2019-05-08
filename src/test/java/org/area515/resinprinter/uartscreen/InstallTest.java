package org.area515.resinprinter.uartscreen;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;

/**
 * Created by zyd on 2018/7/19.
 */

public class InstallTest
{
    @Test
    public void install() throws Exception
    {
        String firmware_path = "D:\\Users\\zyd\\Desktop";

        File[] files = new File(firmware_path).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".hex") ||
                        file.getName().endsWith(".HEX")) {
                    System.out.println(file.getAbsolutePath());
                }
            }
        }
    }
}
