package com.kamal.lc.settings.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SettingResponse {
    private UUID id;
    private String settingKey;
    private String settingGroup;
    private String labelAr;
    private String value;
    private String valueType;
    private String unitLabel;
    private String effectiveFrom;
    private String effectiveTo;
    private String updatedAt;
}
