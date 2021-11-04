package uy.pepeganga.meli.service.services;

import meli.ApiException;
import meli.model.Item;
import uy.com.pepeganga.business.common.entities.*;
import uy.com.pepeganga.business.common.exceptions.PGException;
import uy.com.pepeganga.business.common.models.PriceCostDto;
import uy.pepeganga.meli.service.exceptions.NotFoundException;
import uy.pepeganga.meli.service.exceptions.TokenException;
import uy.pepeganga.meli.service.models.DetailsPublicationsMeliGrid;
import uy.pepeganga.meli.service.models.ItemModel;
import uy.pepeganga.meli.service.models.Pair;
import uy.pepeganga.meli.service.models.dto.MeliSellerAccountFlexDto;
import uy.pepeganga.meli.service.models.publications.ChangeMultipleStatusRequest;
import uy.pepeganga.meli.service.models.publications.DescriptionRequest;
import uy.pepeganga.meli.service.models.publications.PropertiesWithSalesRequest;
import uy.pepeganga.meli.service.models.publications.PropertiesWithoutSalesRequest;

import java.util.List;
import java.util.Map;

public interface IMeliService {

    SellerAccount createAccountByProfile(Integer profileId, SellerAccount sellerAccount);

    SellerAccount updateMeliAccount(Integer accountId, SellerAccount sellerAccount);

    void deleteMeliAccount(Integer accountId);

    List<SellerAccount> meliAccountsByProfileId(Integer profileId);

    SellerAccount findAccountById(Integer accountId);

    boolean createPublicationsFlow(List<ItemModel> items, Integer accountId, Short idMargin) throws NoSuchFieldException, TokenException,
          ApiException;

    boolean createOrUpdateDetailPublicationsMeli(List<ItemModel> items, Integer accountId, Short idMargin);

    DetailsPublicationsMeliGrid updateProductPublished(DetailsPublicationsMeliGrid product) throws ApiException;

    Map<String, Object> updatePropertiesWithoutSales(PropertiesWithoutSalesRequest product, String token, String idPublicationMeli);

    Map<String, Object> updatePropertiesWithSales(PropertiesWithSalesRequest product, String token, String idPublicationMeli);

    Map<String, Object> updateDescription(DescriptionRequest product, String token, String idPublicationMeli);

    Map<String, Object> changeStatusPublication(Integer accountId, int status, String idPublication);

    Map<String, Object> changeStatusMultiplePublications(List<ChangeMultipleStatusRequest> multiple, int status);

    Map<String, Object> deletePublication(Integer accountId, String status, String idPublication);

    Map<String, Object> deletePublicationFailed(Integer id);

    Map<String, Object> deleteSetPublications(Integer accountId, List<Integer> idPublicationsList);

    Map<String, Object> republishPublication(Integer accountId, String idPublication);

    Map<String, Object> republishMultiplePublications(List<ChangeMultipleStatusRequest> multiple);

    void updatePricePublication(Margin margin, Integer idProfile);

    Map<String, Object> synchronizePublication(Integer idProfile, List<Integer> idDetailsPublicationsList);

    void updateStock(List<Pair> pairs, Long idData);

    void synchronizationPublications(List<DetailsPublicationsMeli> detailsList);

    void disableFlexItems(List<DetailsPublicationsMeli> publicationsList, Integer accountId);

    List<MeliSellerAccountFlexDto> getAccountsEnabledOrDisabledFlexByAdmin();

    MeliSellerAccountFlexDto updateAccountsEnabledOrDisabledFlexByAdmin(int accountId, int enableFlex) throws PGException;

    List<MeliCategoryME2> getListCategoriesME2();

    List<MeliCategoryME2> saveCategoriesME2(List<MeliCategoryME2> categoriesME2List);

    Boolean deleteCategoryME2(MeliCategoryME2 category) throws NotFoundException;

    Boolean accountWithEnabledFlex(Integer accountId) throws PGException, TokenException, ApiException;

    void updatePriceCostUYU(List<Integer> idProfileList, List<PriceCostDto> priceCostDtos);

}
