package org.studyplatform.learningservice.learn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseStructureResponse(
        Long id,
        List<Module> modules
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Module(
            Long id,
            Integer orderIndex,
            List<Item> items
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            Long id,
            Integer orderIndex
    ) {
    }
}
