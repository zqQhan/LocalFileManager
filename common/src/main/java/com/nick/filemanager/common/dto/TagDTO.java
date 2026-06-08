package com.nick.filemanager.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tag data transfer object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TagDTO {

    private Long id;
    private String name;
    private String color;
    private String description;
    private Long parentId;
    private Integer fileCount;

    public TagDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public Integer getFileCount() { return fileCount; }
    public void setFileCount(Integer fileCount) { this.fileCount = fileCount; }
}
