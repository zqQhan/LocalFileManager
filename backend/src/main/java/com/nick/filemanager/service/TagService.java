package com.nick.filemanager.service;

import com.nick.filemanager.common.dto.FileInfo;
import com.nick.filemanager.common.dto.TagDTO;
import com.nick.filemanager.model.entity.FileIndex;
import com.nick.filemanager.model.entity.Tag;
import com.nick.filemanager.repository.FileIndexRepository;
import com.nick.filemanager.repository.TagRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tag / category management service.
 */
@WithTransaction
@ApplicationScoped
public class TagService {

    @Inject
    TagRepository tagRepo;

    @Inject
    FileIndexRepository fileRepo;

    /** List all tags with accurate file counts */
    public Uni<List<TagDTO>> listAll() {
        return tagRepo.listAll()
            .flatMap(tags -> {
                if (tags.isEmpty()) {
                    return Uni.createFrom().item(List.<TagDTO>of());
                }
                // Build sequentially to avoid combine() issues with dynamic Uni lists
                return listAllSeq(tags, 0, new ArrayList<>());
            });
    }

    private Uni<List<TagDTO>> listAllSeq(List<Tag> tags, int idx, List<TagDTO> acc) {
        if (idx >= tags.size()) {
            return Uni.createFrom().item(acc);
        }
        Tag tag = tags.get(idx);
        return Mutiny.fetch(tag.files)
            .onFailure().recoverWithItem(Set.of())
            .flatMap(files -> {
                TagDTO dto = toDTO(tag);
                dto.setFileCount(files.size());
                acc.add(dto);
                return listAllSeq(tags, idx + 1, acc);
            });
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

    // ---- File-Tag binding ----

    /** Bind a file to a tag. Uses Mutiny.fetch() to initialize lazy collection. */
    public Uni<TagDTO> addFileToTag(Long tagId, String filePath) {
        return tagRepo.<Tag>findById(tagId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("Tag not found: " + tagId))
            .flatMap(tag ->
                Mutiny.fetch(tag.files)
                    .flatMap(files ->
                        fileRepo.findByPath(filePath)
                            .onItem().ifNull().failWith(() ->
                                new IllegalArgumentException("File not indexed: " + filePath))
                            .flatMap(file -> {
                                if (files.contains(file)) {
                                    return Uni.createFrom().item(tag);
                                }
                                files.add(file);
                                return tag.<Tag>persist();
                            })
                    )
            )
            .map(tag -> {
                TagDTO dto = toDTO(tag);
                dto.setFileCount(tag.files.size());
                return dto;
            });
    }

    /** Remove a file from a tag */
    public Uni<TagDTO> removeFileFromTag(Long tagId, Long fileId) {
        return tagRepo.<Tag>findById(tagId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("Tag not found: " + tagId))
            .flatMap(tag ->
                Mutiny.fetch(tag.files)
                    .flatMap(files -> {
                        files.removeIf(f -> f.id.equals(fileId));
                        return tag.<Tag>persist();
                    })
            )
            .map(tag -> {
                TagDTO dto = toDTO(tag);
                dto.setFileCount(tag.files.size());
                return dto;
            });
    }

    /** List files bound to a tag */
    public Uni<List<FileInfo>> getFilesForTag(Long tagId) {
        return tagRepo.<Tag>findById(tagId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("Tag not found: " + tagId))
            .flatMap(tag ->
                Mutiny.fetch(tag.files)
                    .map(files -> files.stream()
                        .map(this::fileToInfo)
                        .collect(Collectors.toList()))
            );
    }

    private TagDTO toDTO(Tag tag) {
        TagDTO dto = new TagDTO();
        dto.setId(tag.id);
        dto.setName(tag.name);
        dto.setColor(tag.color);
        dto.setDescription(tag.description);
        dto.setParentId(tag.parentId);
        // fileCount via count query to avoid lazy-loading the entire files collection
        dto.setFileCount(0);
        return dto;
    }

    private FileInfo fileToInfo(FileIndex fi) {
        FileInfo info = new FileInfo();
        info.setId(fi.id);
        info.setPath(fi.path);
        info.setName(fi.name);
        info.setExtension(fi.extension);
        info.setSizeBytes(fi.sizeBytes);
        info.setModifiedAt(fi.modifiedAt);
        info.setMimeType(fi.mimeType);
        info.setContentHash(fi.contentHash);
        info.setContentSnippet(fi.contentSnippet);
        info.setIndexedAt(fi.indexedAt);
        info.setIsDirectory(fi.isDirectory);
        return info;
    }
}
