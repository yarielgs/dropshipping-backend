package uy.com.pepeganga.consumingwsstore.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import uy.com.pepeganga.business.common.entities.UpdatesOfSystem;
import uy.com.pepeganga.business.common.models.PriceCostDto;
import uy.com.pepeganga.business.common.models.PriceCostMLDto;
import uy.com.pepeganga.consumingwsstore.client.MeliFeignClient;
import uy.com.pepeganga.consumingwsstore.client.UserFeignClient;
import uy.com.pepeganga.consumingwsstore.models.Pair;
import uy.com.pepeganga.consumingwsstore.repositories.IItemRepository;
import uy.com.pepeganga.consumingwsstore.repositories.ProductsRepository;

@Service
@EnableAsync
public class UpdateProducts implements IUpdateProducts {

   private static final Logger logger = LoggerFactory.getLogger(PurchaseOrdersService.class);

   private final ProductsRepository productRepo;
   private final IItemRepository itemRepo;

   private final UserFeignClient userFeign;
   private final MeliFeignClient meliFeign;

   public UpdateProducts(final ProductsRepository productRepo, final IItemRepository itemRepo, final UserFeignClient userFeign, final MeliFeignClient meliFeign) {
      this.productRepo = productRepo;
      this.itemRepo = itemRepo;
      this.userFeign = userFeign;
      this.meliFeign = meliFeign;
   }

   public void updatePrices(final List<PriceCostDto> priceCostDtos){
      var idProfiles = getIdProfilesEnabled( );
      if(idProfiles != null && !idProfiles.isEmpty()) {
         updatePricesOnMLPublicationsTable(priceCostDtos,idProfiles );
         updatePricesOnDetailsPublicationMeliTableAndMeli(priceCostDtos, idProfiles);
      }
   }

   @Async
   public void updateStockOfPublicationsMeli(List<Pair> pairs, UpdatesOfSystem data){
      try {
         Long id = data.getId();
         meliFeign.updateStock(pairs, id);
      }catch (Exception e) {
         logger.warn("Timeout error waiting for response from meli service to update stock of publications, Error: {}", e.getMessage());
      }
   }

   @Async
   public void updatePricesOnDetailsPublicationMeliTableAndMeli(List<PriceCostDto> priceCostDtos, List<Integer> idProfiles){
      try {
         PriceCostMLDto priceMlDto = new PriceCostMLDto(idProfiles, priceCostDtos);
         meliFeign.updatePriceCost(priceMlDto);
      }catch (Exception e) {
         logger.warn("Posible Timeout waiting for response from meli service to update Price Cost, Error: {}", e.getMessage());
      }
   }

   public void updateStockOfItems(List<String> skus, long stock) {
      try {
         itemRepo.updateStockBySkuSet(stock, skus);
      }catch (Exception e){
         logger.warn("Error updating stock on items table that don´t arrived in synchronization, Error: {}", e.getMessage());
      }
   }

   public void updatePricesOnMLPublicationsTable(final List<PriceCostDto> priceCostDtos, List<Integer> idProfiles){
      for (PriceCostDto costDto : priceCostDtos) {
         productRepo.updatePriceCost(costDto.getSku(), costDto.getPriceCostUyu(), costDto.getPriceCostUsd(), idProfiles );
      }
   }

   public boolean updatestockofproducts(List<Pair> pairsI){
      try {
         pairsI.forEach( i -> {
            productRepo.updateStockBySKU(i.getStock(), i.getSku());
         });
         return true;
      }catch (Exception e){
         logger.error("Error updating stock in mercadolibrepublications table Error: {}", e.getMessage());
         return false;
      }

   }

   public List<Integer> getIdProfilesEnabled() {
      var idProfiles = userFeign.getEnabledProfiles();
      return idProfiles;
   }
}
