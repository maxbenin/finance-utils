package com.maxbenin.financeutils.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRefRepository extends JpaRepository<AccountRef, Long> {
    Optional<AccountRef> findBySourceAndExternalId(String source, String externalId);
}
