package com.raiec.reference.repository;

import com.raiec.reference.entity.DsrItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DsrItemRepository extends JpaRepository<DsrItem, Long> {

    List<DsrItem> findByItemCode(String itemCode);

    Optional<DsrItem> findByItemCodeAndEdition(String itemCode, String edition);

    void deleteByEdition(String edition);
}
