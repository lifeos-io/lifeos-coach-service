package com.lifeos.coach.common.persistence;

import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Base of entities whose ID the application assigns (UUID v7) before saving.
 *
 * <p>Spring Data decides between {@code persist} and {@code merge} by asking the entity whether it is new.
 * Without this, an assigned ID makes every new entity look existing, so {@code save} merges it: Hibernate
 * then manages a copy, and changes to the copy (e.g. a version bump caused by its collections) never reach the
 * object the service keeps using. A new instance is new until it is persisted; loaded instances are not new.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    @Transient
    private boolean persisted;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        persisted = true;
    }
}
