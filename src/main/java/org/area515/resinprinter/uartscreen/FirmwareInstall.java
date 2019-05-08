package org.area515.resinprinter.uartscreen;

import org.area515.resinprinter.printer.ComPortSettings;
import org.area515.resinprinter.printer.Printer;
import org.area515.util.BasicUtillities;
import org.area515.util.IOUtilities;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by zyd on 2018/7/19.
 */

public class FirmwareInstall
{
    private static final int m_pageSize = 128;
    private static final int m_pageCount = 1024;

    private Printer printer;
    private byte seq;

    public FirmwareInstall(Printer printer)
    {
        this.printer = printer;
    }

    public byte[] readHex(String filename) throws Exception
    {
        ArrayList<Byte> arrayList = new ArrayList<>();
        int extraAddr = 0;
        int checksum = 0;
        String line;

        File file = new File(filename);
        BufferedReader reader = null;
        reader = new BufferedReader(new FileReader(file));
        while ((line = reader.readLine()) != null) {
            int recLen = Integer.parseInt(line.substring(1, 3), 16);
            int addr = Integer.parseInt(line.substring(3, 7), 16) + extraAddr;
            int recType = Integer.parseInt(line.substring(7, 9), 16);
            for (int i=0; i<recLen+5; i++) {
                checksum += Integer.parseInt(line.substring(i*2+1, i*2+3), 16);
            }
            checksum &= 0xFF;

            if (recType == 0) {
                while (arrayList.size() < addr + recLen)
                    arrayList.add((byte)0);
                for (int i=0; i<recLen; i++) {
                    arrayList.set(addr+i, (byte)Integer.parseInt(line.substring(i*2+9, i*2+11), 16));
                }
            }
            else if (recType == 2) {
                extraAddr = Integer.parseInt(line.substring(9, 13), 16) * 16;
            }
        }

        return BasicUtillities.listToBytes(arrayList);
    }

    public Printer getPrinter()
    {
        return this.printer;
    }

    public boolean runInstall(String filename)
    {
        try {
            connect();
            Thread.sleep(300);
            enterProgrammingMode();
            byte[] data = readHex(filename);
            writeFlash(data);
            verifyFlash(data);
            connect();
            System.out.println("firmware install succeed");
            return true;
        }
        catch (Exception e) {
            System.out.println("firmware install failed");
            return false;
        }
    }

    public void connect() throws Exception
    {
        getPrinter().getPrinterFirmwareSerialPort().close();
        ComPortSettings settings = getPrinter().getConfiguration().getMachineConfig().getMotorsDriverConfig().getComPortSettings();
        getPrinter().getPrinterFirmwareSerialPort().open(getPrinter().getName(), 1000, settings);
    }

    public void enterProgrammingMode() throws Exception
    {
        this.seq = 1;
        sendMessage(new byte[]{0x01});
        byte[] data = sendMessage(new byte[]{0x10, (byte) 0xc8, 0x64, 0x19, 0x20, 0x00, 0x53, 0x03, (byte) 0xac, 0x53, 0x00, 0x00});
        if (!Arrays.equals(data, new byte[]{0x10, 0x00})) {
            getPrinter().getPrinterFirmwareSerialPort().close();
            throw new Exception("Failed to enter programming mode");
        }
    }

    public void leaveISP() throws Exception
    {
        sendMessage(new byte[]{0x11});
    }

    public byte[] sendISP(byte[] data) throws Exception
    {
        byte[] recv = sendMessage(new byte[]{0x1D, 4, 4, 0, data[0], data[1], data[2], data[3]});
        return BasicUtillities.subBytes(recv, 2, 4);
    }

    public void writeFlash(byte[] flashData) throws Exception
    {
        int pageSize = m_pageSize * 2;
        int flashSize = pageSize * m_pageCount;

        if (flashSize > 0xFFFF)
            sendMessage(new byte[]{0x06, (byte) 0x80, 0x00, 0x00, 0x00});
        else
            sendMessage(new byte[]{0x06, 0x00, 0x00, 0x00, 0x00});

        int loadCount = (flashData.length + pageSize - 1) / pageSize;

        for (int i=0; i<loadCount; i++) {
            byte[] data0 = new byte[]{0x13, (byte) (pageSize >> 8), (byte) (pageSize & 0xFF), (byte) 0xc1, 0x0a, 0x40, 0x4c, 0x20, 0x00, 0x00};
            byte[] data1 = BasicUtillities.subBytes(flashData, i * pageSize, pageSize);
            byte[] data2 = BasicUtillities.bytesCat(data0, data1);
            sendMessage(data2);
            if (i == 440)
                continue;
        }
    }

    public void verifyFlash(byte[] flashData) throws Exception
    {
        int flashSize = m_pageSize * 2 * m_pageCount;

        if (flashSize > 0xFFFF)
            sendMessage(new byte[]{0x06, (byte) 0x80, 0x00, 0x00, 0x00});
        else
            sendMessage(new byte[]{0x06, 0x00, 0x00, 0x00, 0x00});

        int loadCount = (flashData.length + 0xFF) / 0x100;

        for (int i=0; i<loadCount; i++) {
            byte[] recv = sendMessage(new byte[]{0x14, 0x01, 0x00, 0x20});
            recv = BasicUtillities.subBytes(recv, 2, 0x100);
            for (int j=0; j<0x100; j++) {
                if ((i * 0x100 + j < flashData.length) &&
                        flashData[i * 0x100 + j] != recv[j]) {
                    throw new Exception("Verify error");
                }
            }
        }
    }

    public void programChip(ArrayList arrayList) throws Exception
    {
        chipErase();
    }

    public void chipErase() throws Exception
    {
        sendISP(new byte[]{(byte) 0xAC, (byte) 0x80, 0x00, 0x00});
    }

    public byte[] sendMessage(byte[] data) throws Exception
    {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream d = new DataOutputStream(b);
        d.writeByte(0x1B);
        d.writeByte(0x01);
        d.writeShort(data.length);
        d.write(0x0E);
        d.write(data);
        byte checksum = 0;
        for (byte c : b.toByteArray()) {
            checksum ^= c;
        }
        d.writeByte(checksum);

        getPrinter().getPrinterFirmwareSerialPort().write(b.toByteArray());
        this.seq = (byte) ((this.seq + 1) & 0xFF);

        return recvMessage();
    }

    public byte[] recvMessage() throws Exception
    {
        String state = "Start";
        byte checksum = 0;
        byte b;
        int msgSize = 0;
        byte[] data = {};
        byte[] s = {};

        while (true) {
            s = IOUtilities.readBytes(getPrinter().getPrinterFirmwareSerialPort(), 1, 1000, 10);
            if (s == null || s.length < 1)
                throw new Exception("1");
            b = s[0];

            checksum ^= b;
            if (state.equals("Start")) {
                if (b == 0x1B) {
                    state = "GetSeq";
                    checksum = 0x1B;
                }
            }
            else if (state.equals("GetSeq")) {
                state = "MsgSize1";
            }
            else if (state.equals("MsgSize1")) {
                msgSize = b << 8;
                state = "MsgSize2";
            }
            else if (state.equals("MsgSize2")) {
                msgSize |= b;
                state = "Token";
            }
            else if (state.equals("Token")) {
                if (b != 0x0E)
                    state = "Start";
                else {
                    state = "Data";
                    data = new byte[]{};
                }
            }
            else if (state.equals("Data")) {
                data = BasicUtillities.bytesCat(data, new byte[]{b});
                if (data.length == msgSize)
                    state = "Checksum";
            }
            else if (state.equals("Checksum")) {
                if (checksum != 0)
                    state = "Start";
                else
                    return data;
            }
        }
    }

    public void install()
    {
//        getPrinter().getPrinterFirmwareSerialPort().write();
    }
}
