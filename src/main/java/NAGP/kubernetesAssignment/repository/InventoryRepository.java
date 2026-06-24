package NAGP.kubernetesAssignment.repository;

import NAGP.kubernetesAssignment.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {

    @Transactional
    Long deleteByItem(String itemName);
}
