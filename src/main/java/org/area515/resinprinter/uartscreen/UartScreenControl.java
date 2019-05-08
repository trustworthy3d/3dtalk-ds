package org.area515.resinprinter.uartscreen;

import org.area515.resinprinter.job.JobStatus;
import org.area515.resinprinter.job.PrintJob;
import org.area515.resinprinter.network.WirelessNetwork;
import org.area515.resinprinter.printer.ParameterRecord;
import org.area515.resinprinter.printer.Printer;
import org.area515.resinprinter.server.HostProperties;
import org.area515.resinprinter.server.Main;
import org.area515.resinprinter.services.MachineService;
import org.area515.resinprinter.services.PrintableService;
import org.area515.resinprinter.services.PrinterService;
import org.area515.util.BasicUtillities;
import org.area515.util.IOUtilities;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

/**
 * Created by zyd on 2017/8/10.
 * uart screen control
 */

public class UartScreenControl
{
    private String version = "0.4.8";

    //private int Page
    private Thread readThread;
    private Thread writeThread;
//    private Thread testThread;
    private volatile boolean isRead_stop = false;
    private volatile boolean isWrite_stop = false;

    private Printer printer;
    private BlockingQueue<byte[]> writeQueue;
    private int cur_file_selected = -1;
    private int cur_file_page = 0;
    private String cur_file_dir = null;

    private List<WirelessNetwork> network_list = null;
    private int cur_network_selected = -1;
    private int cur_network_page = 0;
    private String network_ssid;
    private String network_psk = "";

    private int numberOfFirstLayers;
    private int firstLayerTime;
    private int layerTime;
    private int resumeLayerTime;
    private double liftDistance;
    private double liftFeedSpeed;
    private double liftRetractSpeed;
    private int delayTimeBeforeSolidify;
    private int delayTimeAfterSolidify;
    private int delayTimeAsLiftedTop;
    private int delayTimeForAirPump;
    private boolean parameterEnabled;
    private boolean detectionEnabled;

    private boolean ledBoardEnabled = false;
    private boolean waterPumpEnabled = false;
    private boolean imageLogoEnabled = false;
    private boolean imageFullEnabled = false;

    private int ledPwmValue = 0;

    private String update_path = "/udiskdir/update-dlp";
//    private String update_path = "C:\\Users\\zyd\\udiskdir\\update-dlp";
    private Timer shutterTimer;


    /*****************machine status******************/
    private JobStatus machine_status = null;
    private String printFileName = "";
//    private long printFileSize = 0;
    private double printProgress = 0;
//    private int printCurrentLayer = 0;
    private int printTotalLayers = 0;
    private long printedTime = 0;
    private long remainingTime = 0;
    /*****************machine status******************/

    /***********************************/

    public UartScreenControl(Printer printer)
    {
        this.printer = printer;
        writeQueue = new ArrayBlockingQueue<byte[]>(64);
    }

    private void startReadThread()
    {
        readThread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                byte[] receive;
                char cmd;

                while (!isRead_stop) {
                    try {
//                        if (getPrinter().getStatus().isNotReady()) {
//                            Thread.sleep(100);
//                            continue;
//                        }

                        receive = IOUtilities.read(getPrinter().getUartScreenSerialPort(), 2000, 10);
                        if (receive == null || receive.length < 9)
                            continue;
                        printBytes(receive);

                        cmd = BasicUtillities.byteArrayToChar(BasicUtillities.subBytes(receive, 4, 2));
                        if (cmd == UartScreenVar.addr_btn_file_ctrl)
                            action_file_ctrl(receive);
                        else if (cmd == UartScreenVar.addr_btn_print_ctrl)
                            action_print_ctrl(receive);
                        else if (cmd == UartScreenVar.addr_btn_network)
                            action_network(receive);
                        else if (cmd == UartScreenVar.addr_btn_language)
                            action_language(receive);
                        else if (cmd == UartScreenVar.addr_btn_parameters)
                            action_parameters(receive);
                        else if (cmd == UartScreenVar.addr_btn_move_control)
                            action_move_control(receive);
                        else if (cmd == UartScreenVar.addr_btn_optical_control)
                            action_optical_control(receive);
                        else if (cmd == UartScreenVar.addr_btn_replace_part)
                            action_replace_part(receive);
                        else if (cmd == UartScreenVar.addr_btn_led_pwm_adjust)
                            action_led_pwm_adjust(receive);
                        else if (cmd == UartScreenVar.addr_btn_clear_trough)
                            action_clear_trough(receive);
                        else if (cmd == UartScreenVar.addr_btn_about)
                            action_about();
                        else if (cmd == UartScreenVar.addr_btn_update_software)
                            action_update_software(receive);
                        else if (cmd == UartScreenVar.addr_btn_update_firmware)
                            action_update_firmware(receive);
                        else if (cmd == UartScreenVar.addr_txt_networkPsk)
                            action_set_network_psk(receive);
                        else if (cmd == UartScreenVar.addr_txt_admin_password)
                            action_set_admin_password(receive);
                        else if (cmd >= UartScreenVar.addr_txt_parameters[0] &&
                                cmd <= UartScreenVar.addr_txt_parameters[UartScreenVar.addr_txt_parameters.length - 1])
                            action_parameters_set(receive);
                        else if (cmd == UartScreenVar.addr_txt_led_pwm)
                            action_set_led_pwm(receive);

                    }
                    catch (InterruptedException | IOException e) {
                        System.out.println(e.toString());
                    }
                }
                System.out.println("read thread stop");
            }
        });
        readThread.start();
    }

    private void startWriteThread()
    {
        writeThread = new Thread(new Runnable() {
            @Override
            public void run()
            {
                byte[] bytes;
                while (!isWrite_stop) {
                    try {
                        bytes = writeQueue.poll(2000, TimeUnit.MILLISECONDS);
                        //bytes = writeQueue.take();
                        if (bytes == null || bytes.length <= 0)
                            continue;
                        getPrinter().getUartScreenSerialPort().write(bytes);
                    }
                    catch (InterruptedException | IOException e) {
                        System.out.println(e.toString());
                    }
                }
                System.out.println("write thread stop");
            }
        });
        writeThread.start();
    }

    /*
    private volatile boolean isTest_stop = true;
    private void startTestThread()
    {
        try
        {
            InputStream stream = new FileInputStream(new File("/opt/cwh/test.cfg"));
            Properties properties = new Properties();
            properties.load(stream);
            int openDelay = new Integer(properties.getProperty("openDelay", "5000"));
            int closeDelay = new Integer(properties.getProperty("closeDelay", "5000"));

            testThread = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    boolean flag = false;
                    while (!isTest_stop)
                    {
                        try
                        {
                            if (getPrinter().getStatus().isNotReady())
                            {
                                Thread.sleep(1000);
                                continue;
                            }
                            if (!flag)
                            {
                                getPrinter().getGCodeControl().executeShutterOn();
                                flag = true;
                            }
                            showImage("/opt/cwh/3DTALK.png");
                            Thread.sleep(openDelay);
                            showImage(null);
                            Thread.sleep(closeDelay);
                        } catch (InterruptedException e)
                        {
                            System.out.println(e.toString());
                        }
                    }
                    System.out.println("write thread stop");
                }
            });
            isTest_stop = false;
            testThread.start();
        }
        catch (IOException e){}
    }
    */

    public void start()
    {
        startReadThread();
        startWriteThread();

        Main.GLOBAL_EXECUTOR.submit(new Runnable() {
            @Override
            public void run()
            {
                if (check_updatable() && HostProperties.Instance().isEnableUpdate()) {
                    close();
                    start_update();
                }
                else {
                    goPage(UartScreenVar.getPagePos(getLanguage(), UartScreenVar.PagePos.Main));
                }
            }
        });
    }

    public void close()
    {
        isRead_stop = true;
        isWrite_stop = true;
        try {
            if (readThread != null) {
                readThread.join();
                readThread = null;
            }
            if (writeThread != null) {
                writeThread.join();
                writeThread = null;
            }
        }
        catch (InterruptedException e) {
            System.out.println(e.toString());
        }
    }

    public Printer getPrinter()
    {
        return this.printer;
    }

    private void writeText(char address, byte[] content)
    {
        byte[] bytes;
        int len = content.length + 3;
        bytes = BasicUtillities.bytesCat(new byte[]{0x5A, (byte) 0xA5, (byte) len, (byte) 0x82}, BasicUtillities.charToBytes(address));
        bytes = BasicUtillities.bytesCat(bytes, content);

        try {
            writeQueue.put(bytes);
        }
        catch (InterruptedException e) {
            System.out.println(e.toString());
        }
    }

    private void writeKey(byte key)
    {
        byte[] bytes = {0x5A, (byte)0xA5, 0x03, (byte)0x80, 0x4F, key};

        try {
            writeQueue.put(bytes);
        }
        catch (InterruptedException e) {
            System.out.println(e.toString());
        }
    }

    private void goPage(int page)
    {
        byte[] bytes;
        bytes = new byte[]{0x5A, (byte) 0xA5, 0x04, (byte) 0x80, 0x03, 0x00, (byte) page};

        try {
            writeQueue.put(bytes);
        }
        catch (InterruptedException e) {
            System.out.println(e.toString());
        }
    }

    private int getLanguage()
    {
        return HostProperties.Instance().getParameterRecord().getLanguage();
    }

    private void printBytes(byte[] bytes)
    {
        String str = "";
        for (byte b : bytes) {
            str += String.format("0x%02x,", b);
        }
        System.out.println(str);
    }

    public void setError(JobStatus status)
    {
        try {
            if (status == JobStatus.ErrorScreen)
                writeText(UartScreenVar.addr_txt_machineStatus, String.format("%-32s", new String(new char[] {0x5C4F, 0x5E55, 0x9519, 0x8BEF})).getBytes("GBK"));//灞忓箷閿欒
            else if (status == JobStatus.ErrorControlBoard)
                writeText(UartScreenVar.addr_txt_machineStatus, String.format("%-32s", new String(new char[] {0x63A7, 0x5236, 0x7248, 0x9519, 0x8BEF})).getBytes("GBK"));//鎺у埗鏉块敊璇�
            while (!writeQueue.isEmpty())
                Thread.sleep(100);
        }
        catch (UnsupportedEncodingException | InterruptedException e) {}
    }

    private List<String> getPrintableList(String whichDir)
    {
        return PrintableService.INSTANCE.getPrintableFiles(whichDir);
    }

    private void filesUpdate(String whichDir, int selected)
    {
        List<String> files = getPrintableList(whichDir);
        String file;

        if (selected < 0)
            selected = 0;

        if (files.size() == 0)
            cur_file_selected = -1;
        else if (selected >= files.size() - 1)
            cur_file_selected = files.size() - 1;
        else
            cur_file_selected = selected;

        if (cur_file_selected < 0)
            cur_file_page = 0;
        else
            cur_file_page = cur_file_selected / 5;

        cur_file_dir = whichDir;

        for (int i = 0; i < 5; i++) {
            file = "";
            if (files.size() > i + cur_file_page * 5) {
                file = files.get(i + cur_file_page * 5);
            }
            try {
                writeText(UartScreenVar.addr_txt_fileList[i], String.format("%-32s", file).getBytes("GBK"));
            }
            catch (UnsupportedEncodingException e) {
                System.out.println(e.toString());
            }
        }
        clearProgBar();
        showFilePageNumber();

        fileHighLight(cur_file_selected);
    }

    private void fileHighLight(int selected)
    {
        if (selected < 0)
            return;

        selected = selected % 5;

        for (int i = 0; i < 5; i++) {
            if (selected == i)
                writeText(UartScreenVar.desc_txt_fileList[i], new byte[] {(byte)0xF8, 0x00}); //the second param is text's color
            else
                writeText(UartScreenVar.desc_txt_fileList[i], new byte[] {(byte)0xFF, (byte)0xFF});
        }
    }

    private void clearProgBar()
    {
        for (int i = 0; i < 5; i++) {
            writeText(UartScreenVar.addr_icon_prog[i], new byte[] {0x00, (byte)65});
        }
    }

    private void showFilePageNumber()
    {
        writeText(UartScreenVar.addr_txt_filePage, new byte[] { (byte) ((cur_file_page >> 8) & 0xFF), (byte) (cur_file_page & 0xFF)});
    }

    private String fileCopy()
    {
        String filename = null;

        List<String> files = getPrintableList("udisk");

        if (files.size() > 0 && cur_file_selected <= files.size() - 1 && cur_file_selected >= 0) {
            filename = files.get(cur_file_selected);
            uploadFromUdiskToLocal(filename);
        }
        return filename;
    }

    private void fileDelete()
    {
        List<String> files = getPrintableList("local");

        if (files.size() > 0 && cur_file_selected <= files.size() - 1 && cur_file_selected >= 0) {
            deleteLocalFile(files.get(cur_file_selected));
            filesUpdate("local", cur_file_selected);
        }
    }

    private void uploadFromUdiskToLocal(String fileName)
    {
        PrintableService.INSTANCE.uploadViaUdisk(fileName, new ProgressCallback() {
            @Override
            public void onProgress(double progress)
            {
                writeText(UartScreenVar.addr_icon_prog[cur_file_selected % 5], new byte[] {0x00, (byte)(39 + progress / 4)});
                System.out.println(progress);
            }
        });
    }

    private void deleteLocalFile(String fileName)
    {
        PrintableService.INSTANCE.deleteFile(fileName);
    }

    private void jobPrint(String fileName)
    {
        Printer printer = getPrinter();
        if (printer.isStarted() && !printer.isPrintInProgress()) {
            PrinterService.INSTANCE.print(fileName, printer.getName());
        }
    }

    private JobStatus jobPause()
    {
        Printer printer = getPrinter();
        return printer.togglePause();
    }

    private void jobStop()
    {
        Printer printer = getPrinter();
        if (printer.isPrintActive()) {
            printer.setStatus(JobStatus.Cancelling);
            setMachineStatus(printer.getStatus(), false, false);
        }
    }

    private void printJob()
    {
        String filename = null;
        if (this.cur_file_dir.equals("udisk")) {
            filename = fileCopy();
        }
        else {
            List<String> files = getPrintableList("local");
            if (files.size() > 0 && cur_file_selected <= files.size() - 1 && cur_file_selected >= 0) {
                filename = files.get(cur_file_selected);
            }
        }

        if (filename != null) {
            jobPrint(filename);
            goPage(UartScreenVar.getPagePos(getLanguage(), UartScreenVar.PagePos.Main));
        }
    }

    private void pauseJob()
    {
        jobPause();
    }

    private void stopJob()
    {
        jobStop();
    }

    private List<WirelessNetwork> getNetworks()
    {
        return MachineService.INSTANCE.getWirelessNetworks();
    }

    private void networksUpdate()
    {
        network_list = getNetworks();
        networkSelect(0);
    }

    private void networkSelect(int selected)
    {
        String network;

        if (selected < 0)
            selected = 0;

        if (network_list == null || network_list.size() == 0)
            cur_network_selected = -1;
        else if (selected >= network_list.size() - 1)
            cur_network_selected = network_list.size() - 1;
        else
            cur_network_selected = selected;

        if (cur_network_selected < 0)
            cur_network_page = 0;
        else
            cur_network_page = cur_network_selected / 5;

        for (int i = 0; i < 5; i++) {
            network = "";
            if (network_list != null && network_list.size() > i + cur_network_page * 5) {
                network = network_list.get(i + cur_network_page * 5).getSsid();
            }
            try {
                writeText(UartScreenVar.addr_txt_network_List[i], String.format("%-32s", network).getBytes("GBK"));
            }
            catch (UnsupportedEncodingException e) {
                System.out.println(e.toString());
            }
        }

        networkHighLight(cur_network_selected);
        if (cur_network_selected < 0)
            setNetworkSsid("");
        else
            setNetworkSsid(network_list.get(cur_network_selected).getSsid());
    }

    private void networkHighLight(int selected)
    {
        if (selected < 0)
            return;

        selected = selected % 5;

        for (int i = 0; i < 5; i++) {
            if (selected == i)
                writeText(UartScreenVar.desc_txt_network_list[i], new byte[] {(byte)0xF8, 0x00}); //the second param is text's color
            else
                writeText(UartScreenVar.desc_txt_network_list[i], new byte[] {(byte)0xFF, (byte)0xFF});
        }
    }

    private void connectNetwork(String ssid, String psk)
    {
        boolean hasSsid = false;
        writeKey((byte)0xF3);
        for (WirelessNetwork network : getNetworks()) {
            if (network.getSsid().equals(ssid)) {
                hasSsid = true;
                network.setPassword(psk);
                if (MachineService.INSTANCE.connectToWifiSSID(network)) {
                    Main.GLOBAL_EXECUTOR.submit(new Runnable() {
                        @Override
                        public void run()
                        {
                            try {
                                int count = 10;
                                String ipAddress;
                                while (count-- > 0) {
                                    Thread.sleep(3000);
                                    ipAddress = getIpAddress();
                                    if (ipAddress != null) {
                                        writeKey((byte)0xF1);
                                        Thread.sleep(500);
                                        writeKey((byte)0xF1);
                                        break;
                                    }
                                    else if (count == 0) {
                                        writeKey((byte)0xF1);
                                        Thread.sleep(500);
                                        writeKey((byte)0xF2);
                                    }
                                }
                            }
                            catch (InterruptedException e) {
                                System.out.println(e.toString());
                            }
                        }
                    });
                }
                else
                {
                    writeKey((byte)0xF1);
                    try {
                        Thread.sleep(500);
                    }
                    catch (InterruptedException e) {
                        System.out.println(e.toString());
                    }
                    writeKey((byte)0xF2);
                }
                break;
            }
        }
        if (!hasSsid) {
            writeKey((byte)0xF1);
            try {
                Thread.sleep(500);
            }
            catch (InterruptedException e) {
                System.out.println(e.toString());
            }
            writeKey((byte)0xF2);
        }
    }

    private String getIpAddress()
    {
        return MachineService.INSTANCE.getLocalIpAddress("wlan0");
    }

    private String getNetworkSsid()
    {
        return this.network_ssid;
    }

    private void setNetworkSsid(String ssid)
    {
        this.network_ssid = ssid;
        writeText(UartScreenVar.addr_txt_networkSsid, String.format("%-32s", ssid).getBytes());
    }

    private String getNetworkPsk()
    {
        return this.network_psk;
    }

    private void setNetworkPsk(String psk)
    {
        this.network_psk = psk;
    }

    private void readParameters()
    {
        numberOfFirstLayers = getPrinter().getConfiguration().getSlicingProfile().getSelectedInkConfig().getNumberOfFirstLayers();
        firstLayerTime = getPrinter().getConfiguration().getSlicingProfile().getSelectedInkConfig().getFirstLayerExposureTime();
        layerTime = getPrinter().getConfiguration().getSlicingProfile().getSelectedInkConfig().getExposureTime();
        resumeLayerTime = getPrinter().getConfiguration().getSlicingProfile().getResumeLayerExposureTime();
        liftDistance = getPrinter().getConfiguration().getSlicingProfile().getLiftDistance();
        liftFeedSpeed = getPrinter().getConfiguration().getSlicingProfile().getLiftFeedSpeed();
        liftRetractSpeed = getPrinter().getConfiguration().getSlicingProfile().getLiftRetractSpeed();
        delayTimeBeforeSolidify = getPrinter().getConfiguration().getSlicingProfile().getDelayTimeBeforeSolidify();
        delayTimeAfterSolidify = getPrinter().getConfiguration().getSlicingProfile().getDelayTimeAfterSolidify();
        delayTimeAsLiftedTop = getPrinter().getConfiguration().getSlicingProfile().getDelayTimeAsLiftedTop();
        delayTimeForAirPump = getPrinter().getConfiguration().getSlicingProfile().getDelayTimeForAirPump();
        parameterEnabled = getPrinter().getConfiguration().getSlicingProfile().getParameterEnabled();
        detectionEnabled = getPrinter().getConfiguration().getSlicingProfile().getDetectionEnabled();

        writeText(UartScreenVar.addr_txt_parameters[0], new byte[] { (byte) ((numberOfFirstLayers >> 8) & 0xFF), (byte) (numberOfFirstLayers & 0xFF)});
        writeText(UartScreenVar.addr_txt_parameters[1], new byte[] { (byte) ((firstLayerTime >> 8) & 0xFF), (byte) (firstLayerTime & 0xFF)});
        writeText(UartScreenVar.addr_txt_parameters[2], new byte[] { (byte) ((layerTime >> 8) & 0xFF), (byte) (layerTime & 0xFF)});
        writeText(UartScreenVar.addr_txt_parameters[3], new byte[] { (byte) ((resumeLayerTime >> 8) & 0xFF), (byte) (resumeLayerTime & 0xFF)});
        writeText(UartScreenVar.addr_txt_parameters[4], new byte[] { (byte) ((((int)(liftDistance*10)) >> 8) & 0xFF), (byte) (((int)(liftDistance*10)) & 0xFF)});
        writeText(UartScreenVar.addr_txt_parameters[5], new byte[] { (byte) ((((int)(liftFeedSpeed*10)) >> 8) & 0xFF), (byte) (((int)(liftFeedSpeed*10)) & 0xFF)});
        writeText(UartScreenVar.addr_txt_parameters[6], new byte[] { (byte) ((((int)(liftRetractSpeed*10)) >> 8) & 0xFF), (byte) (((int)(liftRetractSpeed*10)) & 0xFF)});
        writeText(UartScreenVar.addr_txt_parameters[7], new byte[] { (byte) ((delayTimeBeforeSolidify >> 8) & 0xFF), (byte) (delayTimeBeforeSolidify & 0xFF)});
        writeText(UartScreenVar.addr_txt_parameters[8], new byte[] { (byte) ((delayTimeAfterSolidify >> 8) & 0xFF), (byte) (delayTimeAfterSolidify & 0xFF)});
        writeText(UartScreenVar.addr_txt_parameters[9], new byte[] { (byte) ((delayTimeAsLiftedTop >> 8) & 0xFF), (byte) (delayTimeAsLiftedTop & 0xFF)});
        writeText(UartScreenVar.addr_txt_parameters[10], new byte[] { (byte) ((delayTimeForAirPump >> 8) & 0xFF), (byte) (delayTimeForAirPump & 0xFF)});
        if (parameterEnabled)
            writeText(UartScreenVar.addr_icon_parameter_enabled, new byte[] {0x00, 67});
        else
            writeText(UartScreenVar.addr_icon_parameter_enabled, new byte[] {0x00, 66});
        if (detectionEnabled)
            writeText(UartScreenVar.addr_icon_detection_enabled, new byte[] {0x00, 67});
        else
            writeText(UartScreenVar.addr_icon_detection_enabled, new byte[] {0x00, 66});
    }

    private void saveParameters()
    {
        getPrinter().getConfiguration().getSlicingProfile().getSelectedInkConfig().setNumberOfFirstLayers(numberOfFirstLayers);
        getPrinter().getConfiguration().getSlicingProfile().getSelectedInkConfig().setFirstLayerExposureTime(firstLayerTime);
        getPrinter().getConfiguration().getSlicingProfile().getSelectedInkConfig().setExposureTime(layerTime);
        getPrinter().getConfiguration().getSlicingProfile().setResumeLayerExposureTime(resumeLayerTime);
        getPrinter().getConfiguration().getSlicingProfile().setLiftDistance(liftDistance);
        getPrinter().getConfiguration().getSlicingProfile().setLiftFeedSpeed(liftFeedSpeed);
        getPrinter().getConfiguration().getSlicingProfile().setLiftRetractSpeed(liftRetractSpeed);
        getPrinter().getConfiguration().getSlicingProfile().setDelayTimeBeforeSolidify(delayTimeBeforeSolidify);
        getPrinter().getConfiguration().getSlicingProfile().setDelayTimeAfterSolidify(delayTimeAfterSolidify);
        getPrinter().getConfiguration().getSlicingProfile().setDelayTimeAsLiftedTop(delayTimeAsLiftedTop);
        getPrinter().getConfiguration().getSlicingProfile().setDelayTimeForAirPump(delayTimeForAirPump);
        getPrinter().getConfiguration().getSlicingProfile().setParameterEnabled(parameterEnabled);
        getPrinter().getConfiguration().getSlicingProfile().setDetectionEnabled(detectionEnabled);
        PrinterService.INSTANCE.savePrinter(getPrinter());
    }

    private void setVersion(String version, char type)
    {
        writeText(type, String.format("%-10s", version).getBytes());
    }

    private void setLiftTime()
    {
        String string;

        long ledUsedTime = getPrinter().getLedUsedTime();
        string = String.format("%.1f/%d", ledUsedTime/(60*60*1000.0), 1000);
        writeText(UartScreenVar.addr_txt_lifetime_led, String.format("%-10s", string).getBytes());

        long screenUsedTime = getPrinter().getScreenUsedTime();
        string = String.format("%.1f/%d", screenUsedTime/(60*60*1000.0), 2000);
        writeText(UartScreenVar.addr_txt_lifetime_screen, String.format("%-10s", string).getBytes());
    }

    private void loadAdminAccount(String password)
    {
        writeText(UartScreenVar.addr_txt_admin_password, String.format("%-16s", "").getBytes());
        if (password.equals("123456")) {
            goPage(UartScreenVar.getPagePos(getLanguage(), UartScreenVar.PagePos.Admin));
            setLiftTime();
        }
        else {
            writeKey((byte)0xF1);
        }
    }

    private void showImage(String filePath)
    {
        try {
            if (filePath != null && BasicUtillities.isExists(filePath)) {
                if (getPrinter().getConfiguration().getSlicingProfile().getDetectionLiquidLevelEnabled()) {
                    if (getPrinter().getGCodeControl().executeDetectLiquidLevel().equals("H"))
                        return;
                }

                IOUtilities.executeNativeCommand(new String[]{"/bin/sh", "-c", "sudo xset s off"}, null);
                IOUtilities.executeNativeCommand(new String[]{"/bin/sh", "-c", "sudo xset -dpms"}, null);
                IOUtilities.executeNativeCommand(new String[]{"/bin/sh", "-c", "sudo xset s noblank"}, null);

                File imageFile = new File(filePath);
                BufferedImage image = ImageIO.read(imageFile);
                getPrinter().showImage(image);
            }
            else {
                getPrinter().showBlankImage();
            }
        }
        catch (IOException e) {
            System.out.print(e.toString());
        }
    }

    /****************************notify uartscreen state -start*************************************/
    public void notifyState(Printer printer, PrintJob job)
    {
        setMachineStatus(printer.getStatus(), false, false);

        if (job != null) {
            setPrintFileName(job.getJobName(), false, false);
            setPrintProgress(job.getJobProgress(), true, false);
            setPrintTime(job.getElapsedTime(), job.getEstimateTimeRemaining(), true, false);
        }
        else {
            setPrintFileName("", false, true);
            setPrintProgress(0, false, true);
            setPrintTime(0, 0, true, false);
        }
    }

    private void setMachineStatus(JobStatus status, boolean force, boolean hide)
    {
        if (this.machine_status != status) {
            this.machine_status = status;
            force = true;
        }

        if (hide) {
            writeText(UartScreenVar.addr_txt_machineStatus, String.format("%-32s", "").getBytes());
        }
        else if (force) {
            String string;
            if (getLanguage() == 1)
                string = status.getStateString();
            else
                string = status.getStateStringCN();

            try {
                writeText(UartScreenVar.addr_txt_machineStatus, String.format("%-32s", string).getBytes("GBK"));
                if (status == JobStatus.Printing)
                    writeText(UartScreenVar.addr_icon_pause, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.Pause)});
                else if (status.isPaused())
                    writeText(UartScreenVar.addr_icon_pause, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.Print)});
                else
                    writeText(UartScreenVar.addr_icon_pause, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.Empty0)});

                if (status.isPrintActive())
                    writeText(UartScreenVar.addr_icon_stop, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.Stop)});
                else
                    writeText(UartScreenVar.addr_icon_stop, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.Empty0)});
            }
            catch (UnsupportedEncodingException e) {
                System.out.println(e.toString());
            }
        }
    }

    private void setPrintFileName(String fileName, boolean force, boolean hide)
    {
        if (!this.printFileName.equals(fileName)) {
            this.printFileName = fileName;
            force = true;
        }

        if (hide) {
            writeText(UartScreenVar.addr_txt_printFileName, String.format("%-32s", "").getBytes());
        }
        else if (force) {
            try {
                writeText(UartScreenVar.addr_txt_printFileName, String.format("%-32s", this.printFileName).getBytes("GBK"));
            }
            catch (UnsupportedEncodingException e) {
                System.out.println(e.toString());
            }
        }
    }

    private void setPrintProgress(double printProgress, boolean force, boolean hide)
    {
        if (this.printProgress != printProgress) {
            this.printProgress = printProgress;
            force = true;
        }

        if (hide) {
            writeText(UartScreenVar.addr_txt_printProgress, String.format("%-10s", "").getBytes());
        }
        else if (force) {
            String string = String.format("%.1f%%", printProgress);
            writeText(UartScreenVar.addr_txt_printProgress, String.format("%-10s", string).getBytes());
        }
    }

    private void setPrintTime(long printedTime, long remainingTime, boolean force, boolean hide)
    {
        if (this.printedTime != printedTime || this.remainingTime != remainingTime) {
            this.printedTime = printedTime;
            this.remainingTime = remainingTime;
            force = true;
        }

        if (hide) {
            writeText(UartScreenVar.addr_txt_printTime, String.format("%-32s", "").getBytes());
        }
        else if (force) {
            String string = String.format("%d:%02d:%02d / %d:%02d:%02d",
                    this.printedTime / 3600000,
                    (this.printedTime % 3600000) / 60000,
                    (this.printedTime % 60000) / 1000,
                    this.remainingTime / 3600000,
                    (this.remainingTime % 3600000) / 60000,
                    (this.remainingTime % 60000) / 1000);
            writeText(UartScreenVar.addr_txt_printTime, String.format("%-32s", string).getBytes());
        }
    }
    /****************************notify uartscreen state -end*************************************/

    /***************************action function -start**************************************/
    private void action_file_ctrl(byte[] payload)
    {
        if (payload.length < 9)
            return;
        int key_value = payload[8];

        if(key_value >= 0x00 && key_value <= 0x04) //鏂囦欢閫夋嫨
            filesUpdate(cur_file_dir, cur_file_page * 5 + key_value);
        else if (key_value == 0x05) //鏈湴鏂囦欢
            filesUpdate("local", 0);
        else if (key_value == 0x06) //U鐩樻枃浠�
            filesUpdate("udisk", 0);
        else if (key_value == 0x07) //鍚戝墠缈婚〉
            filesUpdate(cur_file_dir, cur_file_selected - 5);
        else if (key_value == 0x08) //鍚戝悗缈婚〉
            filesUpdate(cur_file_dir, cur_file_selected + 5);
        else if (key_value == 0x09) { //鏂囦欢鍒犻櫎
            if (getPrinter().getStatus().isPrintInProgress())
                return;
            fileDelete();
        }
        else if (key_value == 0x0A) { //鏂囦欢鍒犻櫎
            if (getPrinter().getStatus().isPrintInProgress())
                return;
            fileCopy();
        }
    }

    private void action_print_ctrl(byte[] payload)
    {
        if (payload.length < 9)
            return;
        int key_value = payload[8];

        if (key_value == 0x01 && !getPrinter().getStatus().isPrintInProgress())
            printJob();
        else if (key_value == 0x02)
            pauseJob();
        else if (key_value == 0x03)
            stopJob();
    }

    private void action_network(byte[] payload)
    {
        if (payload.length < 9)
            return;

        int key_value;
        key_value = payload[8];

        if (key_value >= 0x00 && key_value <= 0x04)
            networkSelect(cur_network_page * 5 + key_value);
        else if (key_value == 0x05)
            networkSelect(cur_network_selected - 5);
        else if (key_value == 0x06)
            networkSelect(cur_network_selected + 5);
        else if (key_value == 0x07) {
            if (cur_network_selected >= 0) {
                goPage(UartScreenVar.getPagePos(getLanguage(), UartScreenVar.PagePos.NetworkEdit));
            }
        }
        else if (key_value == 0x08)
            networksUpdate();
        else if (key_value == 0x09) {
            String psk = getNetworkPsk();
            if (psk.length() >= 8)
                connectNetwork(getNetworkSsid(), getNetworkPsk());
            else
                writeKey((byte)0xF2);
        }
    }

    private void action_set_network_psk(byte[] payload)
    {
        String psk = new String(BasicUtillities.subBytes(payload, 7));
        setNetworkPsk(psk.replaceAll("[^\\x20-\\x7E]", ""));
    }

    private void action_parameters(byte[] payload)
    {
        if (payload.length < 9)
            return;

        int key_value;
        key_value = payload[8];

        if (key_value == 0x00) { //璇诲彇鍙傛暟
            readParameters();
        }
        else if (key_value == 0x01) { //淇濆瓨鍙傛暟
            if (getPrinter().getStatus().isPrintInProgress()) {
                writeKey((byte)0xF2);
                return;
            }
            saveParameters();
            writeKey((byte)0xF1);
        }
        else if (key_value == 0x02) {
            parameterEnabled = !parameterEnabled;
            if (parameterEnabled)
                writeText(UartScreenVar.addr_icon_parameter_enabled, new byte[] {0x00, 67});
            else
                writeText(UartScreenVar.addr_icon_parameter_enabled, new byte[] {0x00, 66});
        }
        else if (key_value == 0x03) {
            detectionEnabled = !detectionEnabled;
            if (detectionEnabled)
                writeText(UartScreenVar.addr_icon_detection_enabled, new byte[] {0x00, 67});
            else
                writeText(UartScreenVar.addr_icon_detection_enabled, new byte[] {0x00, 66});
        }
    }

    private void action_parameters_set(byte[] payload)
    {
        if (payload.length < 9)
            return;

        if (getPrinter().getStatus().isPrintInProgress())
            return;

        char cmd = BasicUtillities.byteArrayToChar(BasicUtillities.subBytes(payload, 4, 2));

        int value = ((payload[7] & 0xFF) << 8) + (payload[8] & 0xFF);

        if (cmd == UartScreenVar.addr_txt_parameters[0])
            numberOfFirstLayers = value;
        else if (cmd == UartScreenVar.addr_txt_parameters[1])
            firstLayerTime = value;
        else if (cmd == UartScreenVar.addr_txt_parameters[2])
            layerTime = value;
        else if (cmd == UartScreenVar.addr_txt_parameters[3])
            resumeLayerTime = value;
        else if (cmd == UartScreenVar.addr_txt_parameters[4])
            liftDistance = value/10.0;
        else if (cmd == UartScreenVar.addr_txt_parameters[5])
            liftFeedSpeed = value/10.0;
        else if (cmd == UartScreenVar.addr_txt_parameters[6])
            liftRetractSpeed = value/10.0;
        else if (cmd == UartScreenVar.addr_txt_parameters[7])
            delayTimeBeforeSolidify = value;
        else if (cmd == UartScreenVar.addr_txt_parameters[8])
            delayTimeAfterSolidify = value;
        else if (cmd == UartScreenVar.addr_txt_parameters[9])
            delayTimeAsLiftedTop = value;
        else if (cmd == UartScreenVar.addr_txt_parameters[10])
            delayTimeForAirPump = value;
    }

    private void action_language(byte[] payload)
    {
        if (payload.length < 9)
            return;

        int key_value;
        key_value = payload[8];

        ParameterRecord parameterRecord = HostProperties.Instance().getParameterRecord();
        if (key_value == 0x01)
            parameterRecord.setLanguage(0);
        else if (key_value == 0x02)
            parameterRecord.setLanguage(1);
        HostProperties.Instance().saveParameterRecord(parameterRecord);
        setMachineStatus(getPrinter().getStatus(), true, false);
    }

    private void action_move_control(byte[] payload)
    {
        if (payload.length < 9)
            return;

        if (getPrinter().getStatus().isPrintInProgress())
            return;

        int key_value;
        key_value = payload[8];

        if (key_value == 0x01) {
            //Z杞翠笂绉�
            getPrinter().getGCodeControl().executeSetRelativePositioning();
            getPrinter().getGCodeControl().sendGcode("G1 Z1 F1000");
            getPrinter().getGCodeControl().executeSetAbsolutePositioning();
        }
        else if (key_value == 0x02) {
            //Z杞翠笅绉�
            getPrinter().getGCodeControl().executeSetRelativePositioning();
            getPrinter().getGCodeControl().sendGcode("G1 Z-1 F1000");
            getPrinter().getGCodeControl().executeSetAbsolutePositioning();
        }
        else if (key_value == 0x03) {
            //Z杞村綊闆�
            getPrinter().getGCodeControl().executeZHome();
        }
        else if (key_value == 0x04) {
            //Z杞翠笂绉诲埌椤堕儴
            getPrinter().getGCodeControl().executeSetAbsolutePositioning();
            getPrinter().getGCodeControl().sendGcode("G1 Z140 F1000");
        }
        else if (key_value == 0x05) {
            //Z杞翠笅绉诲埌搴曢儴
            getPrinter().getGCodeControl().executeSetAbsolutePositioning();
            getPrinter().getGCodeControl().sendGcode("G1 Z0 F1000");
        }

    }

    private void action_optical_control(byte[] payload)
    {
        if (payload.length < 9)
            return;

        if (getPrinter().getStatus().isPrintInProgress())
            return;

        int key_value;
        key_value = payload[8];

        if (key_value == 0x00) {
            //杩涘叆鎺у埗椤�
            double temperature = 0;
            String receive = getPrinter().getGCodeControl().executeQueryTemperature();
            Pattern GCODE_Temperature_PATTERN = Pattern.compile("\\s*T:\\s*(-?[\\d\\.]+).*B:(-?[\\d\\.]+).*");
            Matcher matcher = GCODE_Temperature_PATTERN.matcher(receive);
            if (matcher.find()) {
                temperature = Double.parseDouble(matcher.group(2));
            }
            writeText(UartScreenVar.addr_txt_led_temperature, String.format("%-16s", String.format("%.1f", temperature)).getBytes());
        } else if (key_value == 0x01) {
            //鐏澘寮�鍏�
            if (!ledBoardEnabled) {
                getPrinter().getGCodeControl().executeShutterOn();
                ledBoardEnabled = true;
                if (shutterTimer != null) {
                    shutterTimer.cancel();
                    shutterTimer = null;
                }
                shutterTimer = new Timer();
                shutterTimer.schedule(new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        getPrinter().getGCodeControl().executeShutterOff();
                        ledBoardEnabled = false;
                        writeText(UartScreenVar.addr_icon_led_board, new byte[]{0x00, 72});
                    }
                }, 40000);
            } else {
                if (shutterTimer != null) {
                    shutterTimer.cancel();
                    shutterTimer = null;
                }
                getPrinter().getGCodeControl().executeShutterOff();
                ledBoardEnabled = false;
            }
        } else if (key_value == 0x02) {
            //姘村喎寮�鍏�
            if (!waterPumpEnabled) {
                getPrinter().getGCodeControl().executeWaterPumpOn();
                waterPumpEnabled = true;
            } else {
                getPrinter().getGCodeControl().executeWaterPumpOff();
                waterPumpEnabled = false;
            }
        } else if (key_value == 0x03) {
            //棰勭疆鍥惧儚
            if (!imageLogoEnabled) {
                showImage("/opt/cwh/3DTALK.png");
                imageLogoEnabled = true;
                imageFullEnabled = false;
            } else {
                showImage(null);
                imageLogoEnabled = false;
            }
        } else if (key_value == 0x04) {
            //鍏ㄥ睆鐧借壊
            if (!imageFullEnabled) {
                showImage("/opt/cwh/WHITE.png");
                imageFullEnabled = true;
                imageLogoEnabled = false;
            } else {
                showImage(null);
                imageFullEnabled = false;
            }
        } else {
            //閫�鍑洪〉闈�
            if (shutterTimer != null) {
                shutterTimer.cancel();
                shutterTimer = null;
            }
            getPrinter().getGCodeControl().executeShutterOff();
            ledBoardEnabled = false;

            showImage(null);
            imageFullEnabled = false;
            imageLogoEnabled = false;

            if (key_value == 0x05) {
                filesUpdate("local", 0);
            } else if (key_value == 0x07) {
                action_about();
            }
        }

        if (ledBoardEnabled)
            writeText(UartScreenVar.addr_icon_led_board, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.LightSwitch)});
        else
            writeText(UartScreenVar.addr_icon_led_board, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.Empty1)});
        if (waterPumpEnabled)
            writeText(UartScreenVar.addr_icon_water_pump, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.WaterSwitch)});
        else
            writeText(UartScreenVar.addr_icon_water_pump, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.Empty1)});
        if (imageLogoEnabled)
            writeText(UartScreenVar.addr_icon_image_logo, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.PresetImage)});
        else
            writeText(UartScreenVar.addr_icon_image_logo, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.Empty1)});
        if (imageFullEnabled)
            writeText(UartScreenVar.addr_icon_image_full, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.FullScreenImage)});
        else
            writeText(UartScreenVar.addr_icon_image_full, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.Empty1)});

    }

    private void action_set_admin_password(byte[] payload)
    {
        String password = new String(BasicUtillities.subBytes(payload, 7));
        loadAdminAccount(password.replaceAll("[^\\x20-\\x7E]", ""));
    }

    private void action_replace_part(byte[] payload)
    {
        if (payload.length < 9)
            return;

        int key_value;
        key_value = payload[8];

        if (key_value == 1) {
            getPrinter().setLedUsedTime(0);
            writeKey((byte)0xF1);
        }
        else if (key_value == 2) {
            getPrinter().setScreenUsedTime(0);
            writeKey((byte)0xF1);
        }
        setLiftTime();
    }

    private void action_led_pwm_adjust(byte[] payload)
    {
        int key_value;
        key_value = payload[8];

        if (key_value == 0x00) {
            //杩涘叆椤甸潰
            ledPwmValue = new Integer(getPrinter().getGCodeControl().executeReadLedPwmValue());
            writeText(UartScreenVar.addr_txt_led_pwm, new byte[]{(byte) ((ledPwmValue >> 8) & 0xFF), (byte) (ledPwmValue & 0xFF)});
        } else if (key_value == 0x01) {
            //鐏澘寮�鍏�
            if (!ledBoardEnabled) {
                getPrinter().getGCodeControl().executeShutterOn();
                ledBoardEnabled = true;
                if (shutterTimer != null) {
                    shutterTimer.cancel();
                    shutterTimer = null;
                }
                shutterTimer = new Timer();
                shutterTimer.schedule(new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        getPrinter().getGCodeControl().executeShutterOff();
                        ledBoardEnabled = false;
                        writeText(UartScreenVar.addr_icon_led_board, new byte[]{0x00, 72});
                    }
                }, 40000);
            } else {
                if (shutterTimer != null) {
                    shutterTimer.cancel();
                    shutterTimer = null;
                }
                getPrinter().getGCodeControl().executeShutterOff();
                ledBoardEnabled = false;
            }
        } else if (key_value == 0x02) //淇濆瓨鐏澘寮哄害
        {
            getPrinter().getGCodeControl().executeWriteLedPwmValue(ledPwmValue);
            writeKey((byte) 0xF1);
        } else //閫�鍑虹晫闈�
        {
            if (shutterTimer != null) {
                shutterTimer.cancel();
                shutterTimer = null;
            }
            getPrinter().getGCodeControl().executeShutterOff();
            ledBoardEnabled = false;

            if (key_value == 0x04) {
                filesUpdate("local", 0);
            } else if (key_value == 0x05) {
                action_about();
            }
        }

        if (ledBoardEnabled)
            writeText(UartScreenVar.addr_icon_led_board, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.LightSwitch)});
        else
            writeText(UartScreenVar.addr_icon_led_board, new byte[]{0x00, (byte) UartScreenVar.getIconPos(getLanguage(), UartScreenVar.IconPos.Empty1)});
    }

    private void action_clear_trough(byte[] payload)
    {
        if (payload.length < 9)
            return;
        int value = payload[8];

        if (value == 1) {
            writeKey((byte)0xF3);
            getPrinter().getGCodeControl().executeShutterOn();
            showImage("/opt/cwh/WHITE.png");
            if (shutterTimer != null) {
                shutterTimer.cancel();
                shutterTimer = null;
            }
            shutterTimer = new Timer();
            shutterTimer.schedule(new TimerTask() {
                @Override
                public void run()
                {
                    writeKey((byte)0xF1);
                    getPrinter().getGCodeControl().executeShutterOff();
                    showImage(null);
                }
            }, 10000);
        }
    }

    private void action_set_led_pwm(byte[] payload)
    {
        if (payload.length < 9)
            return;

        if (getPrinter().getStatus().isPrintInProgress())
            return;

        ledPwmValue = ((payload[7] & 0xFF) << 8) + (payload[8] & 0xFF);
    }

    private void action_about()
    {
        setVersion(version, UartScreenVar.addr_txt_software_version);
        setVersion(getPrinter().getGCodeControl().executeGetFirmwareVersion(), UartScreenVar.addr_txt_hardware_version);
        String ipAddress = getIpAddress();
        if (ipAddress != null) {
            writeText(UartScreenVar.addr_txt_ipAddress, String.format("%-16s", ipAddress).getBytes());
        }
        else {
            writeText(UartScreenVar.addr_txt_ipAddress, String.format("%-16s", "").getBytes());
        }
        String modelNumber = HostProperties.Instance().getModelNumber();
        writeText(UartScreenVar.addr_txt_modelNumber, String.format("%-16s", modelNumber).getBytes());
    }

    private void action_update_software(byte[] payload)
    {
        if (payload.length < 9)
            return;

        if (getPrinter().getStatus().isPrintInProgress())
            return;

        int value = payload[8];

        if (value == 1) {
            Main.GLOBAL_EXECUTOR.submit(new Runnable() {
                @Override
                public void run()
                {
                    if (check_updatable() && HostProperties.Instance().isEnableUpdate()) {
                        close();
                        start_update();
                    }
                    else {
                        writeKey((byte)0xF2);
                    }
                }
            });
        }
    }

    private void action_update_firmware(byte[] payload)
    {
        if (payload.length < 9)
            return;

        if (getPrinter().getStatus().isPrintInProgress())
            return;

        int value = payload[8];

        try {
            if (value == 1) {
                String filename = check_firmware_updatable();
                if (filename == null) {
                    writeKey((byte)0xF2);
                    return;
                }
                writeKey((byte)0xF3);
                Thread.sleep(500);
                FirmwareInstall firmwareInstall = new FirmwareInstall(getPrinter());
                if (firmwareInstall.runInstall(filename)) {
                    writeKey((byte)0xF1);
                    Thread.sleep(500);
                    writeKey((byte)0xF1);
                }
                else {
                    writeKey((byte)0xF1);
                    Thread.sleep(500);
                    writeKey((byte) 0xF2);
                }
            }
        }
        catch (InterruptedException e) {
            System.out.println(e.toString());
        }
    }

    /***************************action function end**************************************/

    private void start_update()
    {
        try {
            System.out.println("update started");
            getPrinter().getUartScreenSerialPort().write(new byte[]{0x5A, (byte) 0xA5, 0x04, (byte) 0x80, 0x03, 0x00, (byte) UartScreenVar.getPagePos(getLanguage(), UartScreenVar.PagePos.Updating)});
            Thread.sleep(100);
            update_dgus();
            update_filesystem();
            getPrinter().getUartScreenSerialPort().write(new byte[]{0x5A, (byte) 0xA5, 0x04, (byte) 0x80, 0x03, 0x00, (byte) UartScreenVar.getPagePos(getLanguage(), UartScreenVar.PagePos.Updated)});
            System.out.println("update completed");
            while (BasicUtillities.isExists(update_path)) {
                Thread.sleep(1000);
            }
            getPrinter().getUartScreenSerialPort().write(new byte[]{0x5A, (byte) 0xA5, 0x04, (byte) 0x80, 0x03, 0x00, (byte) UartScreenVar.getPagePos(getLanguage(), UartScreenVar.PagePos.Loading)});
            Thread.sleep(100);
            IOUtilities.executeNativeCommand(new String[]{"/bin/sh", "-c", "sudo /etc/init.d/cwhservice restart"}, null);
        }
        catch (IOException | InterruptedException e) {
            System.out.println(e.toString());
        }
    }

    private boolean check_updatable()
    {
        String dgus_path = update_path + "/DWIN_SET";
        String filesystem_path = update_path + "/filesystem";
        String version_path = update_path + "/version";

        try {
            if (!BasicUtillities.isExists(dgus_path) ||
                    !BasicUtillities.isExists(filesystem_path) ||
                    !BasicUtillities.isExists(version_path))
                return false;

            String version_string = BasicUtillities.readAll(version_path);
            String old_version = version.replace(".", "");
            String new_version = version_string.replace(".", "");
            if (Integer.parseInt(old_version) >= Integer.parseInt(new_version))
                return false;
        }
        catch (Exception e) {
            System.out.println(e.toString());
            return false;
        }

        return true;
    }

    private String check_firmware_updatable()
    {
        String firmware_path = update_path + "/firmware";
//        String firmware_path = "D:\\Users\\zyd\\Desktop";

        File[] files = new File(firmware_path).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().endsWith(".hex") ||
                        file.getName().endsWith(".HEX")) {
                    return file.getAbsolutePath();
                }
            }
        }
        return null;
    }

    private void update_filesystem()
    {
        String updateScript = update_path + "/update.sh";
        if (BasicUtillities.isExists(updateScript)) {
            IOUtilities.executeNativeCommand(new String[]{"/bin/sh", "-c", "sudo " + updateScript}, null);
        }
    }

    private void update_dgus()
    {
        String dwinPath = update_path + "/DWIN_SET";

        File[] files = new File(dwinPath).listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (file.getName().toLowerCase().endsWith(".bmp")) {
                System.out.println("update dgus bmp");
                update_dgus_bmp(file);
            }
            if (file.getName().toLowerCase().endsWith(".bin") ||
                    file.getName().toLowerCase().endsWith(".ico") ||
                    file.getName().toLowerCase().endsWith(".hkz")) {
                System.out.println("update dgus others");
                update_dgus_others(file);
            }
        }
    }

    private boolean update_dgus_bmp(File file)
    {
        int byteRead;
        byte[] bmp_head;
        byte[] bmp_body;
        byte[] img5r6b6g;
        int bmp_width, bmp_height;
        int b, g, r;
        byte[] receive;
        int bmp_number;
        InputStream inputStream = null;

        String filename = file.getName();
        if (filename.toLowerCase().endsWith(".bmp")) {
            try {
                bmp_number = Integer.parseInt(filename.replace(".bmp", ""));
                inputStream = new FileInputStream(file);
                bmp_head = new byte[54];
                byteRead = inputStream.read(bmp_head);
                if (byteRead != 54)
                    return false;
                bmp_width = (((bmp_head[21]) & 0xFF) << 24) + (((bmp_head[20]) & 0xFF) << 16) + (((bmp_head[19]) & 0xFF) << 8) + ((bmp_head[18]) & 0xFF);
                bmp_height = (((bmp_head[25]) & 0xFF) << 24) + (((bmp_head[24]) & 0xFF) << 16) + (((bmp_head[23]) & 0xFF) << 8) + ((bmp_head[22]) & 0xFF);
                bmp_body = new byte[bmp_width*bmp_height*3];
                byteRead = inputStream.read(bmp_body);
                if (byteRead != bmp_width*bmp_height*3)
                    return false;
                img5r6b6g = new byte[bmp_width*bmp_height*2];
                for (int i = 0; i < bmp_width*bmp_height; i++) {
                    b = (bmp_body[3*i] & 0xF8);
                    g = (bmp_body[3*i + 1] & 0xFC);
                    r = (bmp_body[3*i + 2] & 0xF8);
                    img5r6b6g[2*i] = (byte) ((r + (g >> 5)) & 0xff);
                    img5r6b6g[2*i + 1] = (byte) (((g << 3) + (b >> 3)) & 0xff);
                }

                getPrinter().getUartScreenSerialPort().write(new byte[]{0x5A,(byte)0xA5,0x06,(byte)0x80,(byte)0xF5,0x5A,0x00,0x00,(byte) bmp_number});
                receive = IOUtilities.read(getPrinter().getUartScreenSerialPort(), 2000, 10);
                if (!"OK".equals(new String(receive)))
                    return false;
                for (int i = bmp_height - 1; i >= 0; i--) {
                    getPrinter().getUartScreenSerialPort().write(BasicUtillities.subBytes(img5r6b6g, bmp_width*2*i, bmp_width*2));
                }
                Thread.sleep(1000);
                getPrinter().getUartScreenSerialPort().write(new byte[]{0x5A,(byte)0xA5,0x04,(byte)0x80,0x03,0x00,(byte) bmp_number});
                Thread.sleep(100);
            }
            catch (IOException | InterruptedException e) {
                System.out.println(e.toString());
            }
            finally {
                try {
                    if (inputStream != null)
                        inputStream.close();
                }
                catch (IOException e) {
                    System.out.println(e.toString());
                }
            }
        }
        else {
            return false;
        }
        return true;
    }

    private boolean update_dgus_others(File file)
    {
        InputStream inputStream = null;
        int fileNumber;
        byte[] dgusCommand;
        byte[] fileData;
        byte[] receive;
        int byteRead;

        String filename = file.getName();
        try {
            fileNumber = Integer.parseInt(filename.substring(0, filename.lastIndexOf(".")));

            inputStream = new FileInputStream(file);
            fileData = new byte[256*1024];
            while ((byteRead = inputStream.read(fileData)) != -1) {
                dgusCommand = new byte[]{0x5A,(byte)0xA5,0x04,(byte)0x80,(byte)0xF3,0x5A,(byte)fileNumber};
                getPrinter().getUartScreenSerialPort().write(dgusCommand);
                receive = IOUtilities.read(getPrinter().getUartScreenSerialPort(), 2000, 10);
                if (!"OK".equals(new String(receive)))
                    return false;
                getPrinter().getUartScreenSerialPort().write(BasicUtillities.subBytes(fileData, 0, byteRead));
                fileNumber++;
                Thread.sleep(1000);
            }

        }
        catch (IOException | InterruptedException e) {
            System.out.println(e.toString());
        }
        finally {
            try {
                if (inputStream != null)
                    inputStream.close();
            }
            catch (IOException e) {
                System.out.println(e.toString());
            }
        }
        return true;
    }

}
