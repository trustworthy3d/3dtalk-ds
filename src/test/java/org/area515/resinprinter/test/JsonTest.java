package org.area515.resinprinter.test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.io.FileReader;
import java.util.List;

/**
 * Created by zyd on 2017/11/1.
 */

public class JsonTest
{
    public static class MaterialDetail {
        @JsonProperty("id")
        private int id;
        @JsonProperty("color")
        private String color;
    }

    public static class MaterialType {
        @JsonProperty("material")
        private List<MaterialDetail> materialDetails;
    }

    @Test
    public void testJson()
    {
        try
        {
            MaterialType materialType = new MaterialType();
            String string = "{\"id\":\"200\", \"color\":\"red\"}";
            ObjectMapper mapper = new ObjectMapper();
            //mapper.writeValueAsString(materialType);
            materialType = mapper.readValue(new FileReader("D:\\Users\\zyd\\Desktop\\haha.txt"), MaterialType.class);
            System.out.println("12");
        }
        catch (Exception e)
        {
            System.out.println(e.toString());
        }
    }
}
