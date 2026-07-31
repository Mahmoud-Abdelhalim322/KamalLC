package com.kamal.lc.settings.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class SettingUpdateRequest {

    @NotBlank(message = "القيمة مطلوبة")
    private String value;

    /** When the new value starts applying. Defaults to today if omitted. */
    private LocalDate effectiveFrom;
}
