package uy.com.pepeganga.consumingwsstore.services;

import java.util.List;

import uy.com.pepeganga.business.common.entities.UpdatesOfSystem;
import uy.com.pepeganga.business.common.models.PriceCostDto;
import uy.com.pepeganga.consumingwsstore.models.Pair;

public interface IUpdateProducts {
   void updatePrices(final List<PriceCostDto> priceCostDtos);

   void updatePricesOnMLPublicationsTable(final List<PriceCostDto> priceCostDtos, List<Integer> idProfiles);

   void updatePricesOnDetailsPublicationMeliTableAndMeli(final List<PriceCostDto> priceCostDtos, List<Integer> idProfiles);

   List<Integer> getIdProfilesEnabled();

   void updateStockOfPublicationsMeli(List<Pair> pairs, UpdatesOfSystem data);

   boolean updatestockofproducts(List<Pair> pairsI);

   void updateStockOfItems(List<String> skus, long stock);
}
