package com.nick.filemanager.repository;

import com.nick.filemanager.model.entity.Tag;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reactive repository for Tag entity.
 */
@ApplicationScoped
public class TagRepository implements PanacheRepositoryBase<Tag, Long> {

    public Uni<Tag> findByName(String name) {
        return find("name", name).firstResult();
    }

    public Uni<Boolean> deleteById(Long id) {
        return delete("id", id).map(count -> count > 0);
    }
}
