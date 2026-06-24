package NAGP.kubernetesAssignment.controller;

import NAGP.kubernetesAssignment.entity.Inventory;
import NAGP.kubernetesAssignment.model.ApiResponse;
import NAGP.kubernetesAssignment.service.InventoryService;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/inventory")
@Slf4j
public class InventoryController {

    @Autowired
    InventoryService inventoryService;

    @PostMapping("/{itemName}")
    public ResponseEntity<ApiResponse<List<Inventory>>> addInventory(@PathVariable("itemName") @Pattern(regexp = ".*[a-zA-Z0-9].*", message = "Item name must contain at least one letter or number") String itemName) {
        ApiResponse<List<Inventory>> apiResponse = new ApiResponse<>();
        List<Inventory> itemList = inventoryService.addInventoryItem(itemName);
        log.info("Inventory added successfully");
        return new ResponseEntity<>(apiResponse.successResponse(itemList, HttpStatus.CREATED.getReasonPhrase()), HttpStatus.CREATED);
    }

    @DeleteMapping("/{itemName}")
    public ResponseEntity<ApiResponse<List<Inventory>>> deleteInventory(@PathVariable("itemName") @Pattern(regexp = ".*[a-zA-Z0-9].*", message = "Item name must contain at least one letter or number") String itemName) {
        ApiResponse<List<Inventory>> apiResponse = new ApiResponse<>();
        List<Inventory> itemList = inventoryService.deleteInventory(itemName);
        log.info("Item with name [{}] deleted successfully", itemName);
        return new ResponseEntity<>(apiResponse.successResponse(itemList, "Inventory Item Deleted Successfully"), HttpStatus.OK);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Inventory>>> getAllInventoryItems() {
        ApiResponse<List<Inventory>> apiResponse = new ApiResponse<>();
        List<Inventory> itemList = inventoryService.getAllInventoryItems();
        log.trace("Available Inventory Items are : [{}]", itemList.toString());
        return new ResponseEntity<>(apiResponse.successResponse(itemList, HttpStatus.OK.getReasonPhrase()), HttpStatus.OK);
    }
}
