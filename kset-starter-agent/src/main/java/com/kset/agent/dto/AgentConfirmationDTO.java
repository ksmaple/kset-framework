package com.kset.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class AgentConfirmationDTO {
    private String confirmationId;
    private String kind;
    private String title;
    private String message;
    private String selectionType;
    private boolean required;
    private String inputPlaceholder;
    private List<Option> options;

    @Data
    public static class Option {
        private String id;
        private String label;
    }
}
