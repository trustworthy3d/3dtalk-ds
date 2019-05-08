package org.area515.resinprinter.uartscreen;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileReader;
import java.util.List;

/**
 * Created by zyd on 2017/11/1.
 */

public class MaterialManager
{
    private MaterialType materialType = null;
    private static final String materialFilePath = "/opt/cwh/material.json";
    public static MaterialManager INSTANCE = new MaterialManager();

    public static class MaterialDetail {
        @JsonProperty("id")
        private int id;
        @JsonProperty("name")
        private String name;
        @JsonProperty("color")
        private String color;
    }

    public static class MaterialType
    {
        @JsonProperty("material")
        private List<MaterialDetail> materialDetails;
    }

    private MaterialDetail getMaterialDetail(int id)
    {
        try
        {
            if (materialType == null)
            {
                materialType = new MaterialType();
                ObjectMapper mapper = new ObjectMapper();
                materialType = mapper.readValue(new FileReader(materialFilePath), MaterialType.class);
            }
            for (MaterialDetail materialDetail : materialType.materialDetails)
            {
                if (materialDetail.id == id)
                    return materialDetail;
            }
            return null;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public String getMaterialName(int id)
    {
        MaterialDetail materialDetail = null;
        materialDetail = getMaterialDetail(id);
        if (materialDetail != null)
            return materialDetail.name;
        return "";
    }

    public String getMaterialColor(int id)
    {
        MaterialDetail materialDetail = null;
        materialDetail = getMaterialDetail(id);
        if (materialDetail != null)
            return materialDetail.color;
        return "";
    }
}
