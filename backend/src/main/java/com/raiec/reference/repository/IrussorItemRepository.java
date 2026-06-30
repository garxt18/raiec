package com.raiec.reference.repository;

import com.raiec.reference.entity.IrussorItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IrussorItemRepository extends JpaRepository<IrussorItem, Long> {

    List<IrussorItem> findByItemCode(String itemCode);

    Optional<IrussorItem> findByItemCodeAndEdition(String itemCode, String edition);

    void deleteByEdition(String edition);
}
