package org.area515.resinprinter.test;

import org.area515.resinprinter.printer.ParameterRecord;
import org.junit.Test;

import java.io.File;
import java.io.UnsupportedEncodingException;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBSource;
import javax.xml.namespace.QName;

/**
 * Created by zyd on 2017/10/11.
 */

public class ParameterRecordTest
{
    private ParameterRecord parameterRecord;
    private File RECORD_DIR = new File(System.getProperty("user.home"), "Record");

    private static <T> T deepCopyJAXB(T object, Class<T> clazz) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        JAXBElement<T> contentObject = new JAXBElement<T>(new QName(clazz.getSimpleName()), clazz, object);
        JAXBSource source = new JAXBSource(jaxbContext, contentObject);
        return jaxbContext.createUnmarshaller().unmarshal(source, clazz).getValue();
    }

    private ParameterRecord getParameterRecord()
    {
        parameterRecord = new ParameterRecord();

        if (!RECORD_DIR.exists() && !RECORD_DIR.mkdirs())
        {
            return parameterRecord;
        }

        File recordFile = new File(RECORD_DIR, "parameter.record");
        if (!recordFile.exists())
        {
            return parameterRecord;
        }

        JAXBContext jaxbContext;
        try
        {
            jaxbContext = JAXBContext.newInstance(ParameterRecord.class);
            Unmarshaller jaxbUnMarshaller = jaxbContext.createUnmarshaller();

            parameterRecord = (ParameterRecord)jaxbUnMarshaller.unmarshal(recordFile);
        }
        catch (JAXBException e)
        {
        }
        return parameterRecord;
    }

    private void saveParameterRecord(ParameterRecord record)
    {
        JAXBContext jaxbContext;
        try
        {
            jaxbContext = JAXBContext.newInstance(ParameterRecord.class);
            Marshaller jaxbMarshaller = jaxbContext.createMarshaller();
            jaxbMarshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);

            if (record != null)
            {
                parameterRecord = deepCopyJAXB(record, ParameterRecord.class);
            }

            File recordFile = new File(RECORD_DIR, "parameter.record");
            jaxbMarshaller.marshal(parameterRecord, recordFile);

        }
        catch (JAXBException e)
        {
        }
    }

    @Test
    public void testRecord()
    {
        ParameterRecord parameterRecord;

        parameterRecord = getParameterRecord();
        parameterRecord.setLedUsedTime(100);
        parameterRecord.setScreenUsedTime(200);
        saveParameterRecord(parameterRecord);

        return;
    }

    @Test
    public void testCode()
    {
        try
        {
            byte[] bytes = new byte[]{(byte) 0xe6, (byte) 0xad, (byte) 0xa3, 'a'};
            String str = new String(bytes);
            byte[] gbk = String.format("%s", str).getBytes("GBK");
            String str1 = new String(new char[] {0x6B63});
            byte[] gbk1 = String.format("%s", str1).getBytes("GBK");
            System.out.println("1");
        }
        catch (UnsupportedEncodingException e)
        {

        }
    }
}
