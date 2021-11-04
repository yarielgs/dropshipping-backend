package uy.com.pepeganga.consumingwsstore.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uy.com.pepeganga.business.common.entities.Item;

@Repository
public interface IItemRepository extends JpaRepository<Item, String>{
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Item i set i.updated =:updated ")
    void updateFieldUpdatedToAll(boolean updated);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Item i set i.stockActual =:stock where i.sku =:sku")
    void updateCurrentStock(long stock, String sku);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update Item i set i.stockActual =:stock where i.sku in (:skus)")
    void updateStockBySkuSet(long stock, List<String> skus);
}
