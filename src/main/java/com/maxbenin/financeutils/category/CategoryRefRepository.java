package com.maxbenin.financeutils.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRefRepository extends JpaRepository<CategoryRef, Long> {
    Optional<CategoryRef> findBySourceAndExternalId(String source, String externalId);
    List<CategoryRef> findBySource(String source);
}
