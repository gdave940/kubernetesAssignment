package NAGP.kubernetesAssignment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "inventory")
@DynamicUpdate
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Inventory {

    @Id
    private String item;

}
