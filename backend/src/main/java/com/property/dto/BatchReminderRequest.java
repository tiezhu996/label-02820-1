package com.property.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchReminderRequest {
    private List<Long> ownerIds;
    private Long templateId;
}
