package uy.com.pepeganga.consumingwsstore.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uy.com.pepeganga.business.common.entities.MercadoLibrePublications;

@Repository
public interface ProductsRepository extends JpaRepository<MercadoLibrePublications, Integer> {

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update MercadoLibrePublications ml set ml.currentStock =:currentStock where ml.sku =:sku ")
    void updateStockBySKU(long currentStock, String sku);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(value = "update mercadolibrepublications dt set dt.price_uyu= :priceUyu, dt.price_costuyu= :priceUyu, dt.price_usd= :priceUsd, "
          + "dt.price_costusd= :priceUsd where dt.sku= :sku and dt.price_costuyu <> :priceUyu and dt.profile_id in (:idEnabledProfile)", nativeQuery = true)
    void updatePriceCost(String sku, double priceUyu, double priceUsd, List<Integer> idEnabledProfile);
}

