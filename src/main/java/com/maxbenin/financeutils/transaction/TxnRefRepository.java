package com.maxbenin.financeutils.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TxnRefRepository extends JpaRepository<TxnRef, Long> {

    Optional<TxnRef> findBySourceAndExternalId(String source, String externalId);

    /** Referências de uma origem cujos lançamentos caem no período. */
    @Query("""
            select r from TxnRef r join fetch r.entry e
            where r.source = :source and e.date between :from and :to
            """)
    List<TxnRef> findBySourceInPeriod(@Param("source") String source,
                                      @Param("from") LocalDate from, @Param("to") LocalDate to);
}
