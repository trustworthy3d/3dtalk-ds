package org.area515.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Created by zyd on 2017/8/14.
 */

public class BasicUtillities
{
    public static byte[] bytesCat(byte[] bytes1, byte[] bytes2)
    {
        if (bytes1 == null)
            return bytes2;
        else if (bytes2 == null)
            return bytes1;

        byte[] bytes3 = new byte[bytes1.length + bytes2.length];
        System.arraycopy(bytes1, 0, bytes3, 0, bytes1.length);
        System.arraycopy(bytes2, 0, bytes3, bytes1.length, bytes2.length);
        return bytes3;
    }

    public static byte[] addByteToArrarys(byte[] bytes, byte b)
    {
        byte[] result = Arrays.copyOf(bytes, bytes.length+1);
        result[result.length-1] = b;
        return result;
    }

    public static byte[] subBytes(byte[] src, int begin, int count)
    {
        byte[] bytes = new byte[count];
        if (src.length - begin <= 0)
            return bytes;
        else if (src.length - begin < count)
            count = src.length - begin;
        System.arraycopy(src, begin, bytes, 0, count);
        return bytes;
    }

    public static byte[] subBytes(byte[] src, int begin)
    {
        int count = src.length - begin;
        return subBytes(src, begin, count);
    }

    public static byte[] charToBytes(char c)
    {
        byte[] bytes = new byte[2];
        bytes[0] = (byte) ((c >> 8) & 0xFF);
        bytes[1] = (byte) (c & 0xFF);
        return bytes;
    }

    public static char byteArrayToChar(byte[] bytes)
    {
        char c;
        if (bytes.length <= 0)
            c = 0;
        if (bytes.length == 1)
            c = (char) bytes[0];
        else
            c = (char) (((bytes[bytes.length - 2] << 8) & 0xFF00) + (bytes[bytes.length - 1] & 0xFF));
        return c;
    }

    public static byte[] listToBytes(List<Byte> list)
    {
        if (list == null || list.size() < 0)
            return null;

        int size = list.size();
        byte[] bytes = new byte[size];
        for (int i=0; i< size; i++) {
            bytes[i] = list.get(i);
        }
        return bytes;
    }

    public static boolean isExists(String path)
    {
        File file = new File(path);
        if (!file.exists())
            return false;
        else
            return true;
    }

    public static String readAll(String filePath)
    {
        File file = new File(filePath);
        BufferedReader reader = null;
        String string = "";
        String tempStr = null;
        try
        {
            reader = new BufferedReader(new FileReader(file));
            while ((tempStr = reader.readLine()) != null)
            {
                string += tempStr;
            }
        }
        catch (IOException e)
        {

        }
        finally
        {
            if (reader != null)
            {
                try
                {
                    reader.close();
                }
                catch (IOException e)
                {

                }
            }
        }
        return string;
    }
}
