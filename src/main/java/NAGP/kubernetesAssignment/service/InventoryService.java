package NAGP.kubernetesAssignment.service;

import NAGP.kubernetesAssignment.entity.Inventory;
import NAGP.kubernetesAssignment.exception.ItemNotFoundException;
import NAGP.kubernetesAssignment.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InventoryService {

    @Autowired
    InventoryRepository inventoryRepository;

    public List<Inventory> addInventoryItem(String itemName) {
        inventoryRepository.saveAndFlush(new Inventory(itemName));
        return getAllInventoryItems();
    }

    public List<Inventory> getAllInventoryItems() {
        return inventoryRepository.findAll(Sort.by(Sort.Direction.ASC, "item"));
    }

    public List<Inventory> deleteInventory(String itemName) {
        if (!(inventoryRepository.deleteByItem(itemName) > 0)) {
            throw new ItemNotFoundException("Item with name '" + itemName + "' not found.");
        }
        return getAllInventoryItems();
    }
}
