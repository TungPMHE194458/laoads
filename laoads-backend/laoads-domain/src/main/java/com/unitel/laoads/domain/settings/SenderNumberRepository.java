package com.unitel.laoads.domain.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SenderNumberRepository extends JpaRepository<SenderNumber, Long> {

    List<SenderNumber> findAllByOrderByIdAsc();

    Optional<SenderNumber> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
