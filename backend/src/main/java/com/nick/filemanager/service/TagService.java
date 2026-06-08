package com.nick.filemanager.service;

import com.nick.filemanager.common.dto.TagDTO;
import com.nick.filemanager.model.entity.Tag;
import com.nick.filemanager.repository.TagRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tag / category management service.
 */
@WithTransaction
@ApplicationScoped
public class TagService {

    @Inject
    TagRepository tagRepo;

    /** List all tags */
    public Uni<List<TagDTO>> listAll() {
        return tagRepo.listAll()
            .map(tags -> tags.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    /** Create a new tag */
    public Uni<TagDTO> create(TagDTO dto) {
        Tag tag = new Tag();
        tag.name = dto.getName();
        tag.color = dto.getColor() != null ? dto.getColor() : "#3B82F6";
        tag.description = dto.getDescription();
        tag.parentId = dto.getParentId();
        return tag.<Tag>persist().map(this::toDTO);
    }

    /** Update an existing tag */
    public Uni<TagDTO> update(Long id, TagDTO dto) {
        return tagRepo.<Tag>findById(id)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("Tag not found: " + id))
            .flatMap(tag -> {
                if (dto.getName() != null) tag.name = dto.getName();
                if (dto.getColor() != null) tag.color = dto.getColor();
                if (dto.getDescription() != null) tag.description = dto.getDescription();
                if (dto.getParentId() != null) tag.parentId = dto.getParentId();
                return tag.<Tag>persist();
            })
            .map(this::toDTO);
    }

    /** Delete a tag */
    public Uni<Boolean> delete(Long id) {
        return tagRepo.deleteById(id);
    }

    private TagDTO toDTO(Tag tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.id);
        dto.setName(tag.name);
        dto.setColor(tag.color);
        dto.setDescription(tag.description);
        dto.setParentId(tag.parentId);
        dto.setFileCount(0); // lazy collection — file count via separate query
        return dto;
    }
}
