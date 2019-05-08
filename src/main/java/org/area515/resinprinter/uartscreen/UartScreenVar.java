package org.area515.resinprinter.uartscreen;

/**
 * Created by zyd on 2018/9/4.
 */

public class UartScreenVar
{
    static final char addr_btn_file_ctrl = 0x0002;
    static final char addr_btn_print_ctrl = 0x0003;
    static final char addr_btn_network = 0x0004;
    static final char addr_btn_language = 0x0005;
    static final char addr_btn_move_control = 0x0006;
    static final char addr_btn_optical_control = 0x0007;
    static final char addr_btn_parameters = 0x0008;
    static final char addr_btn_replace_part = 0x0009;
    static final char addr_btn_led_pwm_adjust = 0x000A;
    static final char addr_btn_clear_trough = 0x000B;
    static final char addr_btn_about = 0x000C;
    static final char addr_btn_update_software = 0x000D;
    static final char addr_btn_update_firmware = 0x000E;

    static final char[] addr_icon_prog = {0x0100, 0x0101, 0x0102, 0x0103, 0x0104};
    static final char addr_icon_pause = 0x0110;
    static final char addr_icon_stop = 0x0111;
    static final char addr_icon_parameter_enabled = 0x0120;
    static final char addr_icon_detection_enabled = 0x0121;
    static final char addr_icon_led_board = 0x0130;
    static final char addr_icon_water_pump = 0x0131;
    static final char addr_icon_image_logo = 0x0132;
    static final char addr_icon_image_full = 0x0133;

    static final char addr_txt_machineStatus = 0x1000;
    static final char addr_txt_printFileName = 0x1020;
    static final char addr_txt_printTime = 0x1040;
    static final char addr_txt_printProgress = 0x1060;
    static final char[] addr_txt_fileList = {0x1100, 0x1120, 0x1140, 0x1160, 0x1180};
    static final char addr_txt_filePage = 0x11A0;
    static final char addr_txt_hardware_version = 0x1200;
    static final char addr_txt_software_version = 0x1210;
    static final char addr_txt_ipAddress = 0x1220;
    static final char addr_txt_modelNumber = 0x1230;
    static final char[] addr_txt_network_List = {0x1300, 0x1320, 0x1340, 0x1360, 0x1380};
    static final char addr_txt_networkSsid = 0x13A0;
    static final char addr_txt_networkPsk = 0x13C0;
    static final char[] addr_txt_material = {0x1400, 0x1410, 0x1420, 0x1430, 0x1440, 0x1450, 0x1460};
    static final char addr_txt_led_temperature = 0x1500;
    static final char addr_txt_admin_password = 0x1600;
    static final char[] addr_txt_parameters = {0x1700, 0x1710, 0x1720, 0x1730, 0x1740, 0x1750, 0x1760, 0x1770, 0x1780, 0x1790, 0x17A0};
    static final char addr_txt_lifetime_led = 0x1800;
    static final char addr_txt_lifetime_screen = 0x1810;
    static final char addr_txt_led_pwm = 0x1900;

    static final char[] desc_txt_fileList = {0x4003, 0x4023, 0x4043, 0x4063, 0x4083};
    static final char[] desc_txt_network_list = {0x4103, 0x4123, 0x4143, 0x4163, 0x4183};

    public enum IconPos {
        Empty0,
        Print,
        Pause,
        Stop,
        Empty1,
        LightSwitch,
        WaterSwitch,
        PresetImage,
        FullScreenImage
    }

    public enum PagePos{
        Loading,
        Updating,
        Updated,
        Main,
        LocalFile,
        UdiskFile,
        Settings,
        About,
        Networks,
        NetworkEdit,
        Admin
    }

    public static int getIconPos(int lang, IconPos iconPos) {
        int pos = 0;
        if (lang == 0) {
            if (iconPos == IconPos.Empty0)
                pos = 68;
            else if (iconPos == IconPos.Print)
                pos = 69;
            else if (iconPos == IconPos.Pause)
                pos = 70;
            else if (iconPos == IconPos.Stop)
                pos = 71;
            else if (iconPos == IconPos.Empty1)
                pos = 72;
            else if (iconPos == IconPos.LightSwitch)
                pos = 73;
            else if (iconPos == IconPos.WaterSwitch)
                pos = 74;
            else if (iconPos == IconPos.PresetImage)
                pos = 75;
            else if (iconPos == IconPos.FullScreenImage)
                pos = 76;
        }
        else {
            if (iconPos == IconPos.Empty0)
                pos = 68;
            else if (iconPos == IconPos.Print)
                pos = 77;
            else if (iconPos == IconPos.Pause)
                pos = 78;
            else if (iconPos == IconPos.Stop)
                pos = 79;
            else if (iconPos == IconPos.Empty1)
                pos = 72;
            else if (iconPos == IconPos.LightSwitch)
                pos = 80;
            else if (iconPos == IconPos.WaterSwitch)
                pos = 81;
            else if (iconPos == IconPos.PresetImage)
                pos = 82;
            else if (iconPos == IconPos.FullScreenImage)
                pos = 83;
        }
        return pos;
    }

    public static int getPagePos(int lang, PagePos pagePos) {
        int pos = 0;

        if (pagePos == PagePos.Loading)
            pos = 0;
        if (pagePos == PagePos.Updating)
            pos = 6;
        if (pagePos == PagePos.Updated)
            pos = 7;
        if (pagePos == PagePos.Main)
            pos = 8;
        if (pagePos == PagePos.LocalFile)
            pos = 9;
        if (pagePos == PagePos.UdiskFile)
            pos = 10;
        if (pagePos == PagePos.Settings)
            pos = 11;
        if (pagePos == PagePos.About)
            pos = 12;
        if (pagePos == PagePos.Networks)
            pos = 13;
        if (pagePos == PagePos.NetworkEdit)
            pos = 14;
        if (pagePos == PagePos.Admin)
            pos = 19;

        if (pagePos != PagePos.Loading && lang == 1)
            pos += 27;

        return pos;
    }

}
