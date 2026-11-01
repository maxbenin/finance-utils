package com.maxbenin.financeutils.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackedAccountRepository extends JpaRepository<TrackedAccount, String> {
}
