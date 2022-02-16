package uy.pepeganga.meli.service.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import meli.ApiException;
import meli.model.*;
import meli.model.Item;

import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uy.com.pepeganga.business.common.entities.*;
import uy.com.pepeganga.business.common.exceptions.PGException;
import uy.com.pepeganga.business.common.models.PriceCostDto;
import uy.com.pepeganga.business.common.utils.date.DateTimeUtilsBss;
import uy.com.pepeganga.business.common.utils.enums.*;
import uy.com.pepeganga.business.common.utils.methods.BurbbleSort;
import uy.pepeganga.meli.service.config.MeliConfig;
import uy.pepeganga.meli.service.exceptions.NotFoundException;
import uy.pepeganga.meli.service.exceptions.TokenException;
import uy.pepeganga.meli.service.models.*;
import uy.pepeganga.meli.service.models.Categories.ShippingPreferences;
import uy.pepeganga.meli.service.models.dto.MeliSellerAccountFlexDto;
import uy.pepeganga.meli.service.models.meli_account_configuration.MeliAccountConfiguration;
import uy.pepeganga.meli.service.models.meli_account_configuration.QueryRequest;
import uy.pepeganga.meli.service.models.publications.*;
import uy.pepeganga.meli.service.repository.*;
import uy.pepeganga.meli.service.utils.FlexResponse;
import uy.pepeganga.meli.service.utils.MapResponseConstants;
import uy.pepeganga.meli.service.utils.MeliErrorCodeReference;
import uy.pepeganga.meli.service.utils.MeliUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class MeliService  implements IMeliService {

    private static final Logger logger = LoggerFactory.getLogger(MeliService.class);

    private static Optional<SellerAccount> accountMeli;

    @Autowired
    SellerAccountRepository sellerAccountRepository;

    @Autowired
    DetailsPublicationMeliRepository detailsPublicationRepository;

    @Autowired
    MarginRepository marginRepo;

    @Autowired
    ImageDetailPublicationRepository imageDPRepository;

    @Autowired
    MercadoLibrePublishRepository mlPublishRepository;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    ProfileRepository profileRepository;

    @Autowired
    IMeliCategoryME2Repository categoryME2Repository;

    @Autowired
    IUpdatesSystemRepository updateSysRepo;

    @Autowired
    IApiService apiService;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    MeliConfig meliConfig;

    @Override
    public SellerAccount createAccountByProfile(Integer profileId, SellerAccount sellerAccount) {
        Optional<Profile> profileFound = profileRepository.findById(profileId);
        if (profileFound.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("Profile with id: %s not found", profileId));
        }
        sellerAccount.setProfile(profileFound.get());
        sellerAccount.setEnabledFlexByAdmin(0);
        return sellerAccountRepository.save(sellerAccount);
    }

    @Override
    public SellerAccount updateMeliAccount(Integer accountId, SellerAccount sellerAccount) {
        SellerAccount accountToUpdated = findAccountById(accountId);
        accountToUpdated.setBusinessName(sellerAccount.getBusinessName());
        accountToUpdated.setBusinessDescription(sellerAccount.getBusinessDescription());
        return sellerAccountRepository.save(accountToUpdated);
    }

    @Override
    public void deleteMeliAccount(Integer accountId) {
        SellerAccount sellerAccountFounded = findAccountById(accountId);
        if (sellerAccountRepository.existsPublication(sellerAccountFounded.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.IM_USED, String.format("This seller account %d has publications", accountId));
        }
        SellerAccount accountToSave = new SellerAccount();
        accountToSave.setId(sellerAccountFounded.getId());
        accountToSave.setProfile(sellerAccountFounded.getProfile());
        accountToSave.setUserIdBss(sellerAccountFounded.getUserIdBss());
        accountToSave.setBusinessDescription(sellerAccountFounded.getBusinessDescription());
        accountToSave.setBusinessName(sellerAccountFounded.getBusinessName());
        accountToSave.setStatus(0);
        sellerAccountRepository.save(accountToSave);
    }

    @Override
    public List<SellerAccount> meliAccountsByProfileId(Integer profileId) {
        Optional<Profile> profileFound = profileRepository.findById(profileId);
        if (profileFound.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("Profile with id: %s not found", profileId));
        }
        return profileFound.get().getSellerAccounts();
    }

    @Override
    public SellerAccount findAccountById(Integer accountId) {
        Optional<SellerAccount> accountFounded = sellerAccountRepository.findById(accountId);
        if (accountFounded.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, String.format("Account with id: %s not found", accountId));
        }
        return accountFounded.get();
    }

    @Override
    public boolean createOrUpdateDetailPublicationsMeli(List<ItemModel> items, Integer accountId, Short idMargin) {
        List<DetailsPublicationsMeli> detailsMeli = new ArrayList<>();
        List<MercadoLibrePublications> meliPublicationsList = new ArrayList<>();
        for (ItemModel iter : items) {
            DetailsPublicationsMeli detail = new DetailsPublicationsMeli();
            DetailsPublicationsMeli detailP = null;
            if (!iter.getSku().isBlank()) {
                detailP = detailsPublicationRepository.findBySKUAndAccountId(iter.getSku(), accountId);
                if (detailP != null) {
                    detail = detailP;
                    detail.setImages(iter.getImages());
                }
            }

            detail.setTitle(iter.getItem().getTitle());
            detail.setAccountMeli(accountId);
            detail.setCategoryMeli(iter.getItem().getCategoryId());
            detail.setMargin(idMargin);
            detail.setPricePublication(iter.getItem().getPrice());
            detail.setPriceEditProduct(iter.getPriceEditProduct());
            detail.setDescription(iter.getItem().getDescription());
            detail.setUserId(getAccountMeli(accountId, true).get().getUserId());
            detail.setPriceCostUSD(iter.getPriceCostUSD());
            detail.setPriceCostUYU(iter.getPriceCostUYU());
            if (detailP == null) {
                detail.setSku(iter.getSku());
                iter.getImages().forEach(i -> i.setId(null));
                detail.setImages(iter.getImages());
            }

            List<String> tags = iter.getItem().getShipping().getTags();
            if (tags == null || tags.isEmpty()) {
                detail.setFlex(0); // Sin flex en la publicación -- el usuario no tiene flex habilitado en ML
            } else {
                for (String f : tags) {
                    if (f.equals("self_service_in"))
                        detail.setFlex(1); //Con flex en la publicación -- el usuario tiene flex habilitado en ML
                    else
                        detail.setFlex(0); // Sin flex en la publicación -- el usuario tiene flex habilitado en ML
                }

            }

            Optional<MercadoLibrePublications> meli = mlPublishRepository.findById(iter.getIdPublicationProduct());
            if (meli.isPresent() && meli.get().getStates() == States.NOPUBLISHED.getId()) {
                detail.setIdMLPublication(meli.get().getId());
                meli.get().setStates((short) 1);
                meliPublicationsList.add(meli.get());
            } else if (meli.isPresent() && meli.get().getStates() == States.PUBLISHED.getId()) {
            } else
                continue;

            if (iter.getItem().getSaleTerms() != null) {
                Optional<SaleTerms> warrantyType = iter.getItem().getSaleTerms().stream().filter(p -> p.getId().equals("WARRANTY_TYPE")).findFirst();
                detail.setWarrantyType(warrantyType.get().getValueName());

                Optional<SaleTerms> warrantyTime = iter.getItem().getSaleTerms().stream().filter(p -> p.getId().equals("WARRANTY_TIME")).findFirst();
                detail.setWarrantyTime(warrantyTime.get().getValueName());
            }
            detail.setStatus(MeliStatusPublications.IN_PROCESS.getValue());
            detail.setDeleted(0);
            detailsMeli.add(detail);
        }
        detailsPublicationRepository.saveAll(detailsMeli);
        if (!meliPublicationsList.isEmpty()) {
            mlPublishRepository.saveAll(meliPublicationsList);
        }
        return true;
    }

    //Global Method that use "createOrUpdateDetailPublicationsMeli, "loadDescriptionToItem" and "createPublication" methods to store and publish
    // one product in ML
    @Override
    public boolean createPublicationsFlow(List<ItemModel> items, Integer accountId, Short idMargin)  {
        List<DetailsPublicationsMeli> detailsToUpdate = new ArrayList<>();

        if (createOrUpdateDetailPublicationsMeli(items, accountId, idMargin)) {            
            
            for (ItemModel item : items) {
               
                //Verify Tokens
                try{
                    if(!verifySecurityTokens(accountId)) {
                        logger.error(" Error getting token Meli Response to accountId: {}", accountId);
                        var fail = setPublicationFail(item, accountId);
                        if(fail != null)
                            detailsToUpdate.add(fail);

                        continue;
                    }
                } catch (ApiException | TokenException e) {
                    logger.error(" Error getting token Meli Response to accountId: {}", accountId);
                    var fail = setPublicationFail(item, accountId);
                    if(fail != null)
                        detailsToUpdate.add(fail);

                    continue;
                }
                
                //code to format images
                Item localItem = item.getItem();
                localItem.getPictures().forEach(i -> {
                    if (!i.getSource().trim().startsWith("http://") && !i.getSource().trim().startsWith("https://")) {
                        String path = (meliConfig.getFreeServerPath() + "/pepeganga/upload/api/bucket/download-file-from-upload-bucket?pathFile=" + i.getSource()).trim();
                        i.setSource(path);
                    }
                });
                Map<String, Object> response = createPublication(localItem);
                if (response.containsKey("response")) {
                    Object obj = response.get("response");
                    DetailsModelResponse detailM = mapper.convertValue(obj, DetailsModelResponse.class);

                    //Load description into publication on Mercado Libre
                    var description = loadDescriptionToItem(new DescriptionRequest(localItem.getDescription()), accountMeli.get().getAccessToken(), detailM.getIdPublication());

                    if (!item.getSku().isBlank()) {
                        DetailsPublicationsMeli detailP = detailsPublicationRepository.findBySKUAndAccountId(item.getSku(), accountId);

                        List<String> tags = detailM.getShipping().getTags();
                        if (tags.isEmpty()) {
                            detailP.setFlex(0); // Sin flex en la publicación -- el usuario no tiene flex habilitado en ML
                        } else {
                            tags.forEach(f -> {
                                if (f.equals("self_service_in"))
                                    detailP.setFlex(1); //Con flex en la publicación -- el usuario tiene flex habilitado en ML
                                else
                                    detailP.setFlex(0); // Sin flex en la publicación -- el usuario tiene flex habilitado en ML
                            });
                        }

                        detailP.setStatus(detailM.getStatus());
                        detailP.setIdPublicationMeli(detailM.getIdPublication());
                        detailP.setLastUpgrade(detailM.getLastUpdated());
                        detailP.setPermalink(detailM.getPermalink());
                        detailsToUpdate.add(detailP);
                    }
                } else {
                    var failP = setPublicationFail(item, accountId);
                    if(failP != null)
                        detailsToUpdate.add(failP);

                }
            } detailsPublicationRepository.saveAll(detailsToUpdate);
        } return true;
    }

    @Override
    public DetailsPublicationsMeliGrid updateProductPublished(DetailsPublicationsMeliGrid product) throws ApiException {
        try {
            Optional<SellerAccount> accountFounded = sellerAccountRepository.findById(product.getAccountMeli());
            Map<String, Object> response = new HashMap<>();
            DetailsPublicationsMeliGrid productResponse = new DetailsPublicationsMeliGrid();
            if (accountFounded.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND.value(), String.format("No se encontro la cuenta: %s", product.getAccountName()));
            } else if (MeliUtils.isExpiredToken(accountFounded.get())) {
                apiService.getTokenByRefreshToken(accountFounded.get());
                accountFounded = sellerAccountRepository.findById(product.getAccountMeli());
            }
            //Encontrar el producto en la base datos
            String sku = product.getSku();
            DetailsPublicationsMeli detailP = detailsPublicationRepository.findBySKUAndAccountId(sku, product.getAccountMeli());

            if (detailP != null) {
                if (detailP.getDescription().trim().equals(product.getDescription().trim())) {
                    DescriptionRequest descriptionRequest = new DescriptionRequest();
                    descriptionRequest.setDescription(product.getDescription());
                    response.put("response", apiService.updateDescription(descriptionRequest, accountFounded.get().getAccessToken(), product.getIdPublicationMeli()));
                }
                //Para las imagenes
                List<Source> sources = new ArrayList<>();
                List<ImagePublicationMeli> newImageList = new ArrayList<>();
                product.getImages().forEach(i -> {
                    if (i.getId() != null) {
                        if (!i.getPhotos().trim().startsWith("http://") && !i.getPhotos().trim().startsWith("https://")) {
                            String path = (meliConfig.getFreeServerPath() + "/pepeganga/upload/api/bucket/download-file-from-upload-bucket?pathFile="
                                  + i.getPhotos()).trim();
                            i.setPhotos(path);
                        }
                        newImageList.add(i);
                    }
                });

                //Ordeno el arreglo segun orden de ubicacion de las imagenes
                List<ImagePublicationMeli> imageOrderList = BurbbleSort.burbbleLowerToHigherByImagesDetails(newImageList);
                for (ImagePublicationMeli image : imageOrderList) {
                    Source source = new Source();
                    source.setSource(image.getPhotos());
                    sources.add(source);
                }

                //Producto Con ventas
                if (product.getSaleStatus() == 1) {
                    PropertiesWithSalesRequest withSaleRequest = new PropertiesWithSalesRequest();
                    withSaleRequest.setPrice(product.getPricePublication());
                    withSaleRequest.setPictures(sources);
                    response.put("response", apiService.updatePropertiesWithSales(withSaleRequest, accountFounded.get().getAccessToken(), product.getIdPublicationMeli()));

                    //Producto Sin ventas
                } else if (product.getSaleStatus() == 0) {
                    PropertiesWithoutSalesRequest withoutSaleRequest = new PropertiesWithoutSalesRequest();
                    withoutSaleRequest.setPrice(product.getPricePublication());
                    withoutSaleRequest.setPictures(sources);
                    withoutSaleRequest.setTitle(product.getTitle());
                    response.put("response", apiService.updatePropertiesWithoutSales(withoutSaleRequest, accountFounded.get().getAccessToken(),
                          product.getIdPublicationMeli()));
                }

                //Construyo objeto para retornar
                if (response.containsKey("response")) {
                    Object obj = response.get("response");
                    DetailsModelResponse detailM = mapper.convertValue(obj, DetailsModelResponse.class);
                    detailP.setLastUpgrade(detailM.getLastUpdated());
                    detailP.setPricePublication(detailM.getPrice());
                    detailP.setMargin(product.getMargin());
                    detailsPublicationRepository.save(detailP);
                    productResponse = product;
                    productResponse.setLastUpgrade(detailM.getLastUpdated());
                    return productResponse;
                } else {
                    logger.error("Fallo actualizando producto en Mercado Libre");
                    throw new ApiException(HttpStatus.CONFLICT.value(), "Fallo actualizando producto en Mercado Libre");
                }
            } else {
                logger.error("No existen publicaciones para el producto a actualizar. Sincronice sus publicaciones.");
                throw new ApiException(HttpStatus.CONFLICT.value(),
                      "No existen publicaciones para el producto a actualizar. Sincronice sus publicaciones.");
            }
        } catch (TokenException e) {
            logger.error(" Error getting token Meli Response: {}", e.getMessage());
            throw new ApiException(e.getCode(), "Error al obtener token de Mercado Libre. Pude que la API este presentando problema de conexión");
        } catch (ApiException e) {
            //comprobar los codigos de Token Vencido
            logger.error(" Error actualizando publicaciones: {}", e.getResponseBody());
            throw new ApiException(HttpStatus.CONFLICT.value(), String.format("Error obteniendo el token de seguridad: %s", e.getResponseBody()));
        } catch (Exception e) {
            //comprobar los codigos de Token Vencido
            logger.error(" Error en el sistema: {}", e.getMessage());
            throw new ApiException(HttpStatus.CONFLICT.value(), String.format("Error en el sistema: %s", e.getMessage()));
        }
    }

    @Override
    public Map<String, Object> deletePublication(Integer accountId, String statusPublication, String idPublication) {
        Map<String, Object> response = new HashMap<>();
        DeletePublicationRequest request = new DeletePublicationRequest();

        DetailsPublicationsMeli details = detailsPublicationRepository.findByIdPublicationMeli(idPublication);

        try {

            if (Objects.isNull(details)) {
                logger.error("Detail Publication with id: {} not found", idPublication);
                response.put(MapResponseConstants.ERROR,
                      new ApiMeliModelException(HttpStatus.NOT_FOUND.value(), String.format("Account with id: %s not found", accountId)));
                return response;
            }
            if (!statusPublication.equals(MeliStatusPublications.CLOSED.getValue())) {
                var result = changeStatusPublication(accountId, MeliStatusPublications.CLOSED.getValue(), idPublication);
                if (!result.containsKey("response")) {
                    if (result.containsKey(MapResponseConstants.MELI_ERROR) && (MeliErrorCodeReference.STATUS_NOT_MODIFIABLE.getCode().trim().equals(result.get(MapResponseConstants.MELI_ERROR)))) {
                        response.putAll(deletePublicationOfSystem(details));
                        //OJO -- Ver como eliminar la referencia del nuevo producto en mercado libre
                    } else {
                        //Hubo un error que ya fue registrado en el metodo que se llamó
                        response.putAll(result);
                    }
                    return response;
                }

                details.setStatus(MeliStatusPublications.CLOSED.getValue());
                detailsPublicationRepository.save(details);
            }
            //Si llega aquí es porque la publicacion ya está en estado closed
            Optional<SellerAccount> accountFounded = getAccountMeli(accountId, false);
            if (accountFounded.isEmpty()) {
                logger.error("Account with id: {} not found", accountId);
                response.put(MapResponseConstants.ERROR,
                      new ApiMeliModelException(HttpStatus.NOT_FOUND.value(), String.format("Account with id: %s not found", accountId)));
                return response;
            } else if (MeliUtils.isExpiredToken(accountFounded.get())) {
                accountFounded = Optional.ofNullable(apiService.getTokenByRefreshToken(accountFounded.get()));
            }
            Object result = apiService.deletePublication(request, accountFounded.get().getAccessToken(), idPublication);
            if (!Objects.isNull(result)) {
                Map<String, Object> map = setProductToNopublishedStatus(details.getIdMLPublication(), States.NOPUBLISHED.getId());
                if (map.containsKey("response")) {
                    details.setDeleted(1);
                    details.setIdMLPublication(-1);
                    detailsPublicationRepository.save(details);
                    response.put("response", "deleted");
                } else {
                    response.putAll(map);
                }
            } else {
                logger.error("Publication '{}' cannot be deleted by Mercado Libre, deleting of system...", idPublication);
                response.putAll(deletePublicationOfSystem(details));
            }
            return response;
        } catch (IllegalArgumentException e) {
            logger.error("The status: you provide is not correct");
            response.put(MapResponseConstants.ERROR, new ApiMeliModelException(HttpStatus.BAD_REQUEST.value(), String.format("The status that you provide is not correct")));
            return response;
        } catch (TokenException e) {
            logger.error(" Error getting token Meli Response: {}", e.getMessage());
            response.put(MapResponseConstants.MELI_ERROR, new ApiMeliModelException(e.getCode(),
                  "Error al obtener token de Mercado Libre. Pude que la API este presentando problema de conexión"));
            return response;
        } catch (ApiException e) {
            if (e.getCode() == 409) {
                logger.warn(String.format("You must wait a few seconds for the change to update to, publicationId: %s, code: {}",
                      idPublication), e.getCode());
                response.put(MapResponseConstants.RESPONSE, "deleted" );
            } else {
                logger.error("Publication '{}' cannot be deleted by Mercado Libre, deleting of system...", idPublication);
                response.putAll(deletePublicationOfSystem(details));
                /*logger.error(String.format("Publication cannot be deleted, publicationId: %s, code: {}, responseBody: {}", idPublication), e.getCode(), e.getResponseBody());
                response.put(MapResponseConstants.MELI_ERROR,
                      new ApiMeliModelException(e.getCode(), String.format("Publication cannot be deleted, publicationId: %s", idPublication)));*/
            }
            return response;
        }
    }

    @Override
    public Map<String, Object> deletePublicationFailed(Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<DetailsPublicationsMeli> details = detailsPublicationRepository.findById(id);
            if (details.isPresent()) {
                response = deletePublicationOfSystem(details.get());
            } else {
                logger.error("Publication not found");
                response.put(ActionResult.NOT_FOUND.getValue(), "Publication not found");
            }
            return response;
        } catch (Exception e) {
            logger.error("Error storing in Data Base: {}", e.getMessage());
            response.put(ActionResult.DATABASE_ERROR.getValue(), "Error storing in database");
            return response;
        }
    }

    private Map<String, Object> deletePublicationOfSystem(DetailsPublicationsMeli details) {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> map = setProductToNopublishedStatus(details.getIdMLPublication(), States.NOPUBLISHED.getId());
            details.setDeleted(1);
            details.setIdMLPublication(-1);
            detailsPublicationRepository.save(details);

            if (map.containsKey("response")) {
                response.put("response", "deleted");
            } else {
                response.putAll(map);
            }

            return response;
        } catch (Exception e) {
            logger.error("Error storing in Data Base: {}", e.getMessage());
            response.put(ActionResult.DATABASE_ERROR.getValue(), "Error storing in database");
            return response;
        }
    }

    @Override
    //Elimina las publicaciones de la lista o toda las publicaciones de una cuenta si la lista está vacia
    public Map<String, Object> deleteSetPublications(Integer accountId, List<Integer> idPublicationsList) {

        List<DetailsPublicationsMeli> detailsList;
        Map<String, Object> response = new HashMap<>();
        AtomicBoolean entry = new AtomicBoolean(false);
        try {
            if (idPublicationsList.isEmpty()) { //Se eliminan todas las publicaciones correspondientes a la cuenta
                detailsList = detailsPublicationRepository.findAllByAccountMeli(accountId);
            } else { //Se eliminan sólo las publicaciones seleccionadas correspondientes a la cuenta
                detailsList = detailsPublicationRepository.findAllById(idPublicationsList);
            }
            //Sincroniza el estado actual de cada publicacion seleccionada
            synchronizationPublications(detailsList); //OJO--verificar que sucede si el estado es Fail

            detailsList.forEach(d -> {
                if (d.getStatus().equals(MeliStatusPublications.FAIL.getValue())) {
                    if (!deletePublicationOfSystem(d).containsKey("response")) {
                        entry.set(true);
                        response.put(ActionResult.PARTIAL.getValue(), "Algunas publicaciones no fueron eliminadas. Revise el estado de estas.");
                    }
                } else {
                    if (!deletePublication(d.getAccountMeli(), d.getStatus(), d.getIdPublicationMeli()).containsKey("response")) {
                        entry.set(true);
                        response.put(ActionResult.PARTIAL.getValue(), "Algunas publicaciones no fueron eliminadas. Revise el estado de estas.");
                    }
                }
            });
            if (entry.get() == false) {
                response.put(ActionResult.DONE.getValue(), "Todos las publicaciones fueron eliminadas.");
            }
            return response;
        } catch (Exception e) {
            logger.error(String.format("The publication cannot to be deleted. Method: deleteSetPublications(), Msg: %s, Error: ", e.getMessage()), e);
            response.put(ActionResult.BAD.getValue(), String.format("Las publicaciones no fueron eliminadas. Error: %s", e.getMessage()));
            return response;
        }
    }

    @Override
    public Map<String, Object> republishPublication(Integer accountId, String idPublication) {
        Map<String, Object> response = new HashMap<>();
        RepublishPublicationRequest request = new RepublishPublicationRequest();

        try {
            DetailsPublicationsMeli details = detailsPublicationRepository.findByIdPublicationMeli(idPublication);
            if (Objects.isNull(details)) {
                logger.error("Detail Publication with id: {} not found", idPublication);
                response.put(MapResponseConstants.ERROR,
                      new ApiMeliModelException(HttpStatus.NOT_FOUND.value(), String.format("Account with id: %s not found", accountId)));
            } else {
                Optional<SellerAccount> accountFounded = sellerAccountRepository.findById(accountId);
                if (accountFounded.isEmpty()) {
                    logger.error("Account with id: {} not found", accountId);
                    response.put(MapResponseConstants.ERROR,
                          new ApiMeliModelException(HttpStatus.NOT_FOUND.value(), String.format("Account with id: %s not found", accountId)));
                } else {
                    if (MeliUtils.isExpiredToken(accountFounded.get())) {
                        accountFounded = Optional.ofNullable(apiService.getTokenByRefreshToken(accountFounded.get()));
                    }
                    request.setListing_type_id("gold_premium");
                    request.setPrice(details.getPricePublication());
                    request.setQuantity((int) itemRepository.findById(details.getSku()).get().getStockActual());
                    Object obj = apiService.republishPublication(request, accountFounded.get().getAccessToken(), idPublication);
                    if (!Objects.isNull(obj)) {
                        DetailsModelResponse detailM = mapper.convertValue(obj, DetailsModelResponse.class);
                        details.setStatus(detailM.getStatus());
                        details.setIdPublicationMeli(detailM.getIdPublication());
                        details.setLastUpgrade(detailM.getLastUpdated());
                        details.setPermalink(detailM.getPermalink());
                        response.put(MapResponseConstants.RESPONSE, detailsPublicationRepository.save(details).getStatus().trim());
                    } else {
                        logger.error("The product do not republished");
                        response.put(MapResponseConstants.MELI_ERROR, new ApiMeliModelException(HttpStatus.NOT_MODIFIED.value(), String.format("The product do not republished")));
                    }
                }
            }
            return response;
        } catch (TokenException e) {
            logger.error(" Error getting token Meli Response: {}", e.getMessage());
            response.put(MapResponseConstants.MELI_ERROR, new ApiMeliModelException(e.getCode(),
                  "Error al obtener token de Mercado Libre. Pude que la API este presentando problema de conexión"));
        } catch (ApiException e) {
            //if(e.getCode() == 400) ya esta eliminada
            logger.error(" Error With MErcado Libre API: {}", e.getMessage());
            response.put(MapResponseConstants.MELI_ERROR, new ApiMeliModelException(e.getCode(), e.getResponseBody()));
        } catch (Exception e) {
            logger.error(" Error of the system: {}", e.getMessage());
            response.put(MapResponseConstants.ERROR, new ApiMeliModelException(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()));
        }
        return response;
    }

    @Override
    public Map<String, Object> republishMultiplePublications(List<ChangeMultipleStatusRequest> multiple) {
        Map<String, Object> response = new HashMap<>();

        for (ChangeMultipleStatusRequest one : multiple) {
            for (String idPublication : one.getPublicationsIds()) {
                var result = republishPublication(one.getAccountId(), idPublication);
                response.putAll(result);
            }
        }
        return response;
    }

    public Map<String, Object> updatePropertiesWithoutSales(PropertiesWithoutSalesRequest product, String token, String idPublicationMeli) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("response", apiService.updatePropertiesWithoutSales(product, token, idPublicationMeli));
            return response;
        } catch (ApiException e) {
            //comprobar los codigos de Token Vencido
            logger.error(" Error obteniendo el token de seguridad: {}", e.getResponseBody());
            response.put(MapResponseConstants.MELI_ERROR, new ApiMeliModelException(e.getCode(), e.getResponseBody()));
            return response;
        }
    }

    public Map<String, Object> updatePropertiesWithSales(PropertiesWithSalesRequest product, String token, String idPublicationMeli) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("response", apiService.updatePropertiesWithSales(product, token, idPublicationMeli));
            return response;
        } catch (ApiException e) {
            //comprobar los codigos de Token Vencido
            logger.error(" Error obteniendo el token de seguridad: {}", e.getResponseBody());
            response.put(MapResponseConstants.MELI_ERROR, new ApiMeliModelException(e.getCode(), e.getResponseBody()));
            return response;
        }
    }

    public Map<String, Object> updateDescription(DescriptionRequest product, String token, String idPublicationMeli) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("response", apiService.updateDescription(product, token, idPublicationMeli));
            return response;
        } catch (ApiException e) {
            //comprobar los codigos de Token Vencido
            logger.error(" Error obteniendo el token de seguridad: {}", e.getResponseBody());
            response.put(MapResponseConstants.MELI_ERROR, new ApiMeliModelException(e.getCode(), e.getResponseBody()));
            return response;
        }
    }

    @Override
    public Map<String, Object> changeStatusPublication(Integer accountId, String status, String idPublication) {

        Map<String, Object> response = new HashMap<>();
        ChangeStatusPublicationRequest request = new ChangeStatusPublicationRequest();

        try {
            request.setStatus(status.trim());
        } catch (IllegalArgumentException e) {
            logger.error("The status: {} that you provide is not correct", status);
            response.put(MapResponseConstants.ERROR,
                  new ApiMeliModelException(HttpStatus.BAD_REQUEST.value(), String.format("The status: %s you provide is not correct", status)));
            return response;
        }

        DetailsPublicationsMeli details = detailsPublicationRepository.findByIdPublicationMeli(idPublication);
        if (Objects.isNull(details)) {
            logger.error("Detail Publication with id: {} not found", idPublication);
            response.put(MapResponseConstants.ERROR,
                  new ApiMeliModelException(HttpStatus.NOT_FOUND.value(), String.format("Account with id: %s not found", accountId)));
        } else {
            Optional<SellerAccount> accountFounded = sellerAccountRepository.findById(accountId);
            if (accountFounded.isEmpty()) {
                logger.error("Account with id: {} not found", accountId);
                response.put(MapResponseConstants.ERROR,
                      new ApiMeliModelException(HttpStatus.NOT_FOUND.value(), String.format("Account with id: %s not found", accountId)));
            } else {
                try {
                    if (MeliUtils.isExpiredToken(accountFounded.get())) {
                        accountFounded = Optional.ofNullable(apiService.getTokenByRefreshToken(accountFounded.get()));
                    }
                    Object result = apiService.changeStatusPublications(request, accountFounded.get().getAccessToken(), idPublication);
                    if (!Objects.isNull(result)) {
                        details.setStatus(status.trim());
                        response.put(MapResponseConstants.RESPONSE, detailsPublicationRepository.save(details).getStatus().trim());
                    } else {
                        logger.error("Publication not changed: status to change: {}, publicationId: {}", status, idPublication);
                        response.put(MapResponseConstants.ERROR, ChangeStatusPublicationType.ofCode(-1).getStatus());
                    }
                } catch (TokenException e) {
                    logger.error(" Error getting token Meli Response: {}", e.getMessage());
                    response.put(MapResponseConstants.MELI_ERROR, new ApiMeliModelException(e.getCode(),
                          "Error al obtener token de Mercado Libre. Pude que la API este presentando problema de conexión"));
                } catch (ApiException e) {
                    try {
                        MeliResponseBodyException bodyException = mapper.readValue(e.getResponseBody(), MeliResponseBodyException.class);
                        if (Objects.isNull(bodyException.getCause()) || bodyException.getCause().length == 0) {
                            logger.error("Error Meli: {}", bodyException.getMessage());
                            response.put(MapResponseConstants.MELI_ERROR, bodyException.getMessage());
                        } else {
                            Object[] obj = bodyException.getCause();
                            MeliCauseError causeError = mapper.convertValue(obj[0], MeliCauseError.class);
                            response.put(MapResponseConstants.MELI_ERROR, causeError.getCode());
                        }
                    } catch (Exception ex) {
                        logger.error(" Error parsing Meli Exception Response", ex);
                    }
                }

            }
        }

        return response;
    }

    public Map<String, Object> changeStatusMultiplePublications(List<ChangeMultipleStatusRequest> multiple, String status) {
        Map<String, Object> response = new HashMap<>();

        for (ChangeMultipleStatusRequest one : multiple) {
            for (String idPublication : one.getPublicationsIds()) {
                var result = changeStatusPublication(one.getAccountId(), status, idPublication);
                response.putAll(result);
            }
        }
        return response;
    }

    ///
    /// Actualiza los precios de la tabla Details y de ML para los cambios de Margenes.
    ///
    @Override
    public void updatePricePublication(Margin margin, Integer idProfile) {
        List<SellerAccount> accountList;
        List<Integer> accountIdList;
        List<DetailsPublicationsMeli> detailsPublicationUpdatedList = new ArrayList<>();
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<Profile> profileDb = profileRepository.findById(idProfile);
            if (!profileDb.isPresent()) {
                logger.error("Error, the profile with id {} not found: ", idProfile);
                return;
            }
            accountList = profileDb.get().getSellerAccounts();
            accountIdList = accountList.stream().map(SellerAccount::getId).collect(Collectors.toList());
            List<DetailsPublicationsMeli> detailsPublicationList = detailsPublicationRepository.findByIdAccountsAndMargin(accountIdList, margin.getId(), 0);

            //Update price and pending to all publications
            for (DetailsPublicationsMeli detail : detailsPublicationList) {
                Integer price;
                if (margin.getType() == MarginType.PERCENT.getCode()) {
                    price = Math.toIntExact(Math.round((detail.getPriceCostUYU() * (margin.getValue() / 100)) + detail.getPriceCostUYU()));
                } else {
                    price = Math.toIntExact(Math.round(detail.getPriceCostUYU() + margin.getValue()));
                }
                detail.setPendingMarginUpdate(true);
                detail.setPricePublication(price);
            }

            detailsPublicationRepository.saveAll(detailsPublicationList);

            //Update all publications in ML if these are in 'active' status
            List<SellerAccount> finalAccountList = accountList;
            for (DetailsPublicationsMeli detail : detailsPublicationList) {
                if (detail.getStatus().equals(MeliStatusPublications.ACTIVE.getValue()) && detail.getSpecialPaused() == 0) {
                    ChangePriceRequest changePrice = new ChangePriceRequest(detail.getPricePublication());
                    Optional<SellerAccount> accountFounded = finalAccountList.stream().filter(a -> a.getId() == detail.getAccountMeli()).findFirst();
                    try {
                        if (MeliUtils.isExpiredToken(accountFounded.get())) {
                            accountFounded = Optional.ofNullable(apiService.getTokenByRefreshToken(accountFounded.get()));
                        }

                        Object obj = apiService.updatePricePublication(changePrice, accountFounded.get().getAccessToken(), detail.getIdPublicationMeli());
                        //comprobar codigo de actualizado -- 200 OK
                        detail.setPendingMarginUpdate(false);
                        detailsPublicationUpdatedList.add(detail);
                    } catch (ApiException e) {
                        logger.error(" Error de Mercado Libre: {}", e.getResponseBody());
                    } catch (TokenException e) {
                        logger.error(" Error getting token Meli Response: {}", e.getMessage());
                    }
                }
            }

            if (!detailsPublicationUpdatedList.isEmpty()) {
                detailsPublicationRepository.saveAll(detailsPublicationUpdatedList);
            }
        } catch (Exception e) {
            logger.error(" Error of the system: {}", e.getMessage());
        }
    }

    ///
    /// Actualiza todos los precios en tabla y ML si el valor costo se actualizó
    ///
    @Override
    public void updatePriceCostUYU(List<Integer> idProfileList, List<PriceCostDto> priceCostDtos) {
        List<SellerAccount> accountList;
        List<Integer> accountIdList;

        for (int idProfile : idProfileList) {
            try {
                Optional<Profile> profileDb = profileRepository.findById(idProfile);
                if (!profileDb.isPresent()) {
                    logger.error("Error, the profile with id %s not found: {}: ", idProfile);
                    continue;
                }
                accountList = profileDb.get().getSellerAccounts();
                accountIdList = accountList.stream().map(SellerAccount::getId).collect(Collectors.toList());

                for (PriceCostDto priceCostDto: priceCostDtos) {
                    var dPublicationList = detailsPublicationRepository.findByDistintPriceCostUyu(accountIdList, priceCostDto.getSku(), priceCostDto.getPriceCostUyu(), 0);

                    if(dPublicationList != null && !dPublicationList.isEmpty()) {
                        //Update price and pending to all publications in details table
                        for (DetailsPublicationsMeli detail : dPublicationList) {
                            int price = 0;
                            Optional<Margin> margin = marginRepo.findById(detail.getMargin());

                            if (margin.isPresent()) {
                                if (margin.get().getType() == MarginType.PERCENT.getCode()) {
                                    price = Math.toIntExact(Math.round((priceCostDto.getPriceCostUyu() * (margin.get().getValue() / 100)) + priceCostDto.getPriceCostUyu()));
                                } else {
                                    price = Math.toIntExact(Math.round(priceCostDto.getPriceCostUyu() + margin.get().getValue()));
                                }
                            } else {
                                price = Math.toIntExact(Math.round(priceCostDto.getPriceCostUyu()));
                            }

                            detail.setPendingMarginUpdate(true);
                            detail.setPricePublication(price);
                            detail.setPriceCostUYU(priceCostDto.getPriceCostUyu());
                            detail.setPriceCostUSD(priceCostDto.getPriceCostUsd());
                        }

                        detailsPublicationRepository.saveAll(dPublicationList);
                    }
                }

            } catch (Exception e) {
                logger.error(" Error of the system: {}", e.getMessage());
            }
        }

    }

    ///
    /// Actualiza todos los precios pendientes por actualizar en Meli.
    ///
    @Override
    public void UpdateProductsPending() {
        int isDeletedOrSPaused = 0;
        String status = "active";

        try {

            Collection<SellerAccount> accounts = new ArrayList<>();
            Collection<DetailsPublicationsMeli> pendingPublications = detailsPublicationRepository.findPendingUpdatePublications(isDeletedOrSPaused,
                  status, isDeletedOrSPaused);

            for (DetailsPublicationsMeli pending : pendingPublications) {
                try {
                    var local_acc = accounts.stream().filter(a -> a.getId().equals(pending.getAccountMeli())).findFirst();
                    if (local_acc.isPresent()) {
                        PropertiesWithSalesRequest changePrice = new PropertiesWithSalesRequest();
                        changePrice.setPrice(pending.getPricePublication());
                        if (updatePropertiesWithSales(changePrice, local_acc.get().getAccessToken(), pending.getIdPublicationMeli()).containsKey("response")) {
                            detailsPublicationRepository.updatePendingMarginUpdate(pending.getId(), false);
                        }

                    } else {
                        var repo_acc = sellerAccountRepository.findById(pending.getAccountMeli());
                        if (repo_acc.isPresent()) {
                            if (MeliUtils.isExpiredToken(repo_acc.get())) {
                                repo_acc = Optional.ofNullable(apiService.getTokenByRefreshToken(repo_acc.get()));
                            }
                            accounts.add(repo_acc.get());

                            PropertiesWithSalesRequest changePrice = new PropertiesWithSalesRequest();
                            changePrice.setPrice(pending.getPricePublication());

                            if(updatePropertiesWithSales(changePrice, repo_acc.get().getAccessToken(), pending.getIdPublicationMeli()).containsKey("response")) {
                                detailsPublicationRepository.updatePendingMarginUpdate(pending.getId(), false);
                            }

                        }
                    }
                } catch (ApiException e) {
                    logger.error(" Error de Mercado Libre: ", e);
                } catch (TokenException e) {
                    logger.error(" Error getting token Meli Response: {}", e.getMessage());
                }
            }
        }catch (Exception e) {
            logger.error(" Error updating price in Meli: ", e);
        }
    }

    //Sincroniza las publicaciones del sistema con Mercado Libre. (Actualiza los estados y el precio si tuvo algún cambio)
    @Override
    public Map<String, Object> synchronizePublication(Integer idProfile, List<Integer> idDetailsPublicationsList) {

        Map<String, Object> response = new HashMap<>();
        try {
            List<DetailsPublicationsMeli> detailsList = detailsPublicationRepository.findAllById(idDetailsPublicationsList);
            if (!detailsList.isEmpty()) {
                synchronizationPublications(detailsList);

                //Actualiza las publicaciones con cambios pendientes
                List<DetailsPublicationsMeli> toUpdateList = detailsList
                      .stream()
                      .filter(d -> d.getStatus().equals(MeliStatusPublications.ACTIVE.getValue()) && d.getSpecialPaused() == 0 && d.getPendingMarginUpdate())
                      .collect(Collectors.toList());
                if (!toUpdateList.isEmpty()) {
                    response.putAll(updatePriceMeliOfActivePublications(idProfile, toUpdateList));
                } else {
                    response.put(MapResponseConstants.RESPONSE, "No existen publicaciones para actualizar");
                }
            }
            return response;
        } catch (Exception e) {
            logger.error("Error of systems, Error: {} ", e.getMessage());
            response.put(ActionResult.ERROR.getValue(), String.format("Error of systems: %s: ", e.getMessage()));
            return response;
        }
    }

    @Override
    //Sincroniza los estados de las publicaciones en Meli
    public void synchronizationPublications(List<DetailsPublicationsMeli> detailsList) {
        List<DetailsPublicationsMeli> toUpdate = new ArrayList<>();
        //Sincroniza los estados de las publicaciones en ML con la base datos
        detailsList.forEach(d -> {
            Map<String, Object> status = apiService.getStatusPublication(d.getIdPublicationMeli());
            if (status.containsKey(MapResponseConstants.RESPONSE)) {
                Object obj = status.get(MapResponseConstants.RESPONSE);
                MeliCodeResponse detailM = mapper.convertValue(obj, MeliCodeResponse.class);
                if (!detailM.getBody().getStatus().equals(d.getStatus())) {
                    d.setStatus(detailM.getBody().getStatus());
                    toUpdate.add(d);
                }
            }
        });
        if (!toUpdate.isEmpty()) {
            logger.info("Updating {} publication by synchronization", toUpdate.size());
            detailsPublicationRepository.saveAll(toUpdate);
        }
    }

    @Override
    public void updateStock(List<Pair> pairs, Long idData) {
        logger.info(" Begin updating of stock in Mercado Libre");
        AtomicBoolean isGood = new AtomicBoolean(true);
        try {
            UpdatesOfSystem data = getCurrentUpdateOfSystem(idData);

            pairs.forEach(pair -> {
                List<DetailsPublicationsMeli> detailsList = new ArrayList<>();
                detailsList = detailsPublicationRepository.findAllBySku(pair.getSku());
                List<SellerAccount> accountsMeli = new ArrayList<>();
                if (detailsList != null) {
                    detailsList.forEach(d -> {
                        if (d.getStatus().equals(MeliStatusPublications.ACTIVE.getValue()) || d.getStatus().equals(MeliStatusPublications.PAUSED.getValue())) {
                            if (accountsMeli.isEmpty() || !accountsMeli.stream().filter(p -> p.getId().equals(d.getAccountMeli())).findFirst().isPresent()) {
                                try {
                                    Optional<SellerAccount> seller = sellerAccountRepository.findById(d.getAccountMeli());
                                    if (seller.isPresent()) {
                                        if (MeliUtils.isExpiredToken(seller.get())) {
                                            seller = Optional.ofNullable(apiService.getTokenByRefreshToken(seller.get()));
                                        }
                                        accountsMeli.add(seller.get());
                                        //Actualizo stock en ML
                                        ChangeStockRequest request = new ChangeStockRequest();
                                        request.setAvailable_quantity(pair.getStock());
                                        logger.info(" Updating publication: {} with stock: {} in Mercado Libre", d.getIdPublicationMeli(), pair.getStock());
                                        apiService.updateStock(request, seller.get().getAccessToken(), d.getIdPublicationMeli());
                                        logger.info(" Publication updated successfully...");
                                    }
                                } catch (ApiException e) {
                                    logger.error(" Error updating stock in mercado libre, Methods: updateStock(), {}", e.getResponseBody());
                                    isGood.set(false);

                                    String data1 = data.getMessage();
                                    data.setMessage(data1 + " Error updating stock in mercado libre, Methods: updateStock();");
                                    data.setEndDate(DateTimeUtilsBss.getDateTimeAtCurrentTime().toDate());
                                    data.setFinishedSync(false);
                                    updateSysRepo.save(data);
                                } catch (TokenException e) {
                                    logger.error(" Error getting token in method updateStock(): {}", e.getMessage());
                                    logger.error(" Code of the error: {}", e.getCode());
                                    isGood.set(false);

                                    String data1 = data.getMessage();
                                    data.setMessage(data1 + " Error getting token, Methods: updateStock();");
                                    data.setEndDate(DateTimeUtilsBss.getDateTimeAtCurrentTime().toDate());
                                    data.setFinishedSync(false);
                                    updateSysRepo.save(data);
                                }
                            } else {
                                try {
                                    Optional<SellerAccount> seller1 = accountsMeli.stream().filter(p -> p.getId().equals(d.getAccountMeli())).findFirst();
                                    if (MeliUtils.isExpiredToken(seller1.get())) {
                                        seller1 = Optional.ofNullable(apiService.getTokenByRefreshToken(seller1.get()));

                                    }
                                    accountsMeli.add(seller1.get());
                                    //Actualizo stock en ML
                                    ChangeStockRequest request = new ChangeStockRequest();
                                    request.setAvailable_quantity(pair.getStock());
                                    apiService.updateStock(request, seller1.get().getAccessToken(), d.getIdPublicationMeli());
                                } catch (ApiException e) {
                                    logger.error(" Error updating stock in mercado libre, API: call to ML, Methods: updateStock(), {}", e.getResponseBody());
                                    isGood.set(false);

                                    String data1 = data.getMessage();
                                    data.setMessage(data1 + " Error updating stock in mercado libre, API: call to ML, Methods: updateStock();");
                                    data.setEndDate(DateTimeUtilsBss.getDateTimeAtCurrentTime().toDate());
                                    data.setFinishedSync(false);
                                    updateSysRepo.save(data);
                                } catch (TokenException e) {
                                    logger.error(" Error getting token in method updateStock(): {}", e.getMessage());
                                    logger.error(" Code of the error: {}", e.getCode());
                                    isGood.set(false);

                                    String data1 = data.getMessage();
                                    data.setMessage(data1 + " Error getting token, Methods: updateStock();");
                                    data.setEndDate(DateTimeUtilsBss.getDateTimeAtCurrentTime().toDate());
                                    data.setFinishedSync(false);
                                    updateSysRepo.save(data);
                                }
                            }

                        }
                    });
                }
            });
            //todo terminó OK
            if (isGood.get() && data.getEndDate() == null) {
                logger.info("IMPORTANT: STARTING TO UPDATE PUBLICATIONS IN MERCADO LIBRE COMPLETED CORRECTLY ....");
                data.setMessage("Synchronization Completed...");
                data.setEndDate(DateTimeUtilsBss.getDateTimeAtCurrentTime().toDate());
                data.setFinishedSync(true);
                updateSysRepo.save(data);
            }
        } catch (Exception e) {
            logger.error(" Error getting publications of Database in the Detail Publications Meli table, Methods: updateStock(), {}", e.getMessage());
            logger.error("IMPORTANT: THE UPGRATE OF PUBLICATIONS IN MERCADO LIBRE WAS INTERRUPTED BY ERRORS ....");
            UpdatesOfSystem data = getCurrentUpdateOfSystem(idData);
            data.setMessage(data.getMessage() + " Error getting token, Methods: updateStock();");
            data.setEndDate(DateTimeUtilsBss.getDateTimeAtCurrentTime().toDate());
            data.setFinishedSync(false);
            updateSysRepo.save(data);
        }

    }

    @Override
    //ahora no se utiliza pero puede utilizarse para adicionarlo al sincronizar
    public void disableFlexItems(List<DetailsPublicationsMeli> publicationsList, Integer accountId) {
        try {
            if (!publicationsList.isEmpty()) {
                Optional<SellerAccount> accountFounded = getAccountMeli(accountId, false);
                if (accountFounded.isEmpty()) {
                    logger.error("Not Found, Account with id {} not found: ", accountId);
                    throw new Exception(String.format("Account with id: %s not found", accountId));
                } else if (MeliUtils.isExpiredToken(accountFounded.get())) {
                    accountFounded = Optional.ofNullable(apiService.getTokenByRefreshToken(accountFounded.get()));
                }
                for (DetailsPublicationsMeli item : publicationsList) {
                    if (item.getStatus().equals(MeliStatusPublications.ACTIVE.getValue()) || item.getStatus().equals(MeliStatusPublications.PAUSED.getValue())) {
                        try {
                            Object obj = apiService.isFlexInItem(item.getIdPublicationMeli(), accountFounded.get().getAccessToken());
                            MeliResponseBodyException detailC = mapper.convertValue(obj, MeliResponseBodyException.class);
                            //Si tiene habilitado envios Flex?
                            if (detailC.getStatus() == FlexResponse.No_Content.getStatus()) {
                                Object obj1 = apiService.disableFlexInItem(item.getIdPublicationMeli(), accountFounded.get().getAccessToken());
                                MeliResponseBodyException detailD = mapper.convertValue(obj, MeliResponseBodyException.class);
                                if (detailD.getStatus() == FlexResponse.No_Content.getStatus()) {
                                    logger.info("FLEX shipping disabled for publication: {} ", item.getIdPublicationMeli());
                                } else {
                                    logger.warn("The disable FLEX send operation produces the following message: {}, to the publicaction: {}",
                                          detailD.getError(), item.getIdPublicationMeli());
                                }
                            }
                        } catch (ApiException e) {
                            logger.warn("The disable FLEX send operation produces the following message: {}, to the publicaction: {}", e.getResponseBody(), item.getIdPublicationMeli());
                            logger.info("If error's code is 404 then the Item have not Flex or not exist.");
                        }
                    }
                }
            }
        } catch (ApiException e) {
            logger.error(" Error de Mercado Libre: {}", e.getResponseBody());
        } catch (TokenException e) {
            logger.error(" Error getting token Meli Response: {}", e.getMessage());
        } catch (Exception e) {
            logger.error(" Error in the proccess: {}", e.getMessage());
        }
    }

    @Override
    public List<MeliSellerAccountFlexDto> getAccountsEnabledOrDisabledFlexByAdmin() {
        List<SellerAccount> sellerAccounts = sellerAccountRepository.findAllBySynchronizedAccount();
        return sellerAccounts.stream().map(sellerAccount -> {
            MeliSellerAccountFlexDto flexDto = new MeliSellerAccountFlexDto();
            flexDto.setEnabledFlex(sellerAccount.getEnabledFlexByAdmin() > 0);
            flexDto.setProfileName(sellerAccount.getProfile().getFirstName());
            flexDto.setAccountName(sellerAccount.getBusinessName());
            flexDto.setId(sellerAccount.getId());
            return flexDto;
        }).collect(Collectors.toList());
    }

    @Override
    public MeliSellerAccountFlexDto updateAccountsEnabledOrDisabledFlexByAdmin(int accountId, int enableFlex) throws PGException {
        Optional<SellerAccount> sellerAccount = sellerAccountRepository.findById(accountId);
        if (sellerAccount.isEmpty()) {
            throw new NotFoundException("Seller Account Not Found", HttpStatus.NOT_FOUND);
        }
        sellerAccount.get().setEnabledFlexByAdmin(enableFlex);
        SellerAccount accountSaved = sellerAccountRepository.save(sellerAccount.get());
        MeliSellerAccountFlexDto dto = new MeliSellerAccountFlexDto();

        dto.setEnabledFlex(accountSaved.getEnabledFlexByAdmin() > 0);
        dto.setId(accountSaved.getId());
        dto.setProfileName(accountSaved.getProfile().getBusinessName());
        dto.setAccountName(accountSaved.getBusinessName());
        return dto;
    }

    //pendiente de errores
    @Override
    public List<MeliCategoryME2> getListCategoriesME2() {
        return categoryME2Repository.findAll();
    }

    //pendiente de errores
    @Override
    public List<MeliCategoryME2> saveCategoriesME2(List<MeliCategoryME2> categoriesME2List) {
        return categoryME2Repository.saveAll(categoriesME2List);
    }

    //pendiente de errores
    @Override
    public Boolean deleteCategoryME2(MeliCategoryME2 category) throws NotFoundException {
       /* if(!categoryME2Repository.exists(category)) {
            throw new NotFoundException("Categoría no encontrada", HttpStatus.NOT_FOUND);
        }*/
        categoryME2Repository.delete(category);
        return true;
    }

    //Ver configuracion del vendedor //  -- tratar excepciones
    public Boolean accountWithEnabledFlex(Integer accountId) throws PGException, TokenException, ApiException {
        Optional<SellerAccount> sellerAccount = sellerAccountRepository.findById(accountId);
        if (sellerAccount.isEmpty()) {
            throw new NotFoundException("Seller Account Not Found", HttpStatus.NOT_FOUND);
        }
        if (MeliUtils.isExpiredToken(sellerAccount.get())) {
            sellerAccount = Optional.ofNullable(apiService.getTokenByRefreshToken(sellerAccount.get()));
        }

        QueryRequest obj = new QueryRequest(sellerAccount.get().getUserId());
        Object accountConfig = null;
        try {
            accountConfig = apiService.showConfigurationSeller(obj, sellerAccount.get().getAccessToken());
        } catch (ApiException e) {
            //if(e.getCode() == 404 && !e.getResponseBody().isBlank() ) {
            MeliResponseBodyException exception = null;
            try {
                exception = new MeliResponseBodyException(e.getResponseBody());
            } catch (ParseException pe) {
                logger.error("Error parseando exception, Method: accountWithEnabledFlex(), Service: meli-service, Error: ", pe);
                throw new PGException(pe.getMessage(), pe.getLocalizedMessage(), pe.getErrorType());
            }
            if (exception != null && exception.getMessage().contains("can't get adoption for user")) {
                if (exception.getStatus() == 404 || exception.getStatus() == 400) {
                    return false; // la excepción indica que el user no tiene "adoption" y significa que no cumple con los requisitos para que ML habilite Flex.
                }
            }
            // }
            //En cualquier otro caso, lanzar el error de tipo PGException
            logger.error("Error desconocido de Mercado Libre, Method: accountWithEnabledFlex(), Service: meli-service, Error: ", e);
            throw new PGException(exception.getMessage(), exception.getError(), exception.getStatus());
        }
        if (!Objects.isNull(accountConfig)) {
            MeliAccountConfiguration meliAccountConfig = mapper.convertValue(accountConfig, MeliAccountConfiguration.class);
            if ((meliAccountConfig.getConfiguration().getAdoption() != null && meliAccountConfig
                  .getConfiguration()
                  .getAdoption()
                  .getDelivery_window()
                  .equals("same_day")) || (meliAccountConfig.getConfiguration().getAdoption() != null && meliAccountConfig
                  .getConfiguration()
                  .getAdoption()
                  .getDelivery_window()
                  .equals("next_day"))) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public Set<String> getShippingModeOfCategories(String idCategory) {
        try {
            var obj = apiService.getShippingModeOfCategories(idCategory);
            var shipping = mapper.convertValue(obj, ShippingPreferences.class);
            return shipping.getLogistics().stream().map(m -> m.getMode()).collect(Collectors.toSet());
        } catch (ApiException e) {
            return Set.of();
        }
    }

    /**** Metodos auxiliares ****/
    private Optional<SellerAccount> getAccountMeli(Integer accountId, boolean search) {
        if (accountMeli == null || search == true) {
            accountMeli = sellerAccountRepository.findById(accountId);
        } else if (!accountMeli.isPresent()) {
            accountMeli = sellerAccountRepository.findById(accountId);
        }
        return accountMeli;
    }

    //Actualiza los precios de las publicaciones activas en Meli
    private Map<String, Object> updatePriceMeliOfActivePublications(Integer idProfile, List<DetailsPublicationsMeli> detailsPublicationList) {
        //Update all publications in ML if these are in 'active' status
        Map<String, Object> response = new HashMap<>();
        Optional<Profile> profileDb = profileRepository.findById(idProfile);
        if (!profileDb.isPresent()) {
            logger.error("Not Found, the profile with id {} not found ", idProfile);
            return (Map<String, Object>) response.put("Error", String.format("Error, the profile with id %s not found.", idProfile));
        }
        List<DetailsPublicationsMeli> listDetails = new ArrayList<>();
        List<SellerAccount> finalAccountList = profileDb.get().getSellerAccounts();
        for (DetailsPublicationsMeli detail : detailsPublicationList) {

            ChangePriceRequest changePrice = new ChangePriceRequest(detail.getPricePublication());
            Optional<SellerAccount> accountFounded = finalAccountList.stream().filter(a -> a.getId() == detail.getAccountMeli()).findFirst();
            try {
                if (MeliUtils.isExpiredToken(accountFounded.get())) {
                    accountFounded = Optional.ofNullable(apiService.getTokenByRefreshToken(accountFounded.get()));
                }

                Object obj = apiService.updatePricePublication(changePrice, accountFounded.get().getAccessToken(), detail.getIdPublicationMeli());
                //comprobar codigo de actualizado -- 200 OK
                detail.setPendingMarginUpdate(false);
                listDetails.add(detail);

            } catch (ApiException e) {
                logger.error(" Error de Mercado Libre: {}", e.getResponseBody());
                return (Map<String, Object>) response.put("Error", String.format("Error de Mercado Libre: %s", e.getResponseBody()));
            } catch (TokenException e) {
                logger.error(" Error getting token Meli Response: {}", e.getMessage());
                return (Map<String, Object>) response.put("Meli_Error", String.format("Error getting token Meli Response: %s", e.getMessage()));
            }

        }

        if (!listDetails.isEmpty()) {
            detailsPublicationRepository.saveAll(listDetails);
        }
        return (Map<String, Object>) response.put(MapResponseConstants.RESPONSE, String.format("All publications were updated"));
    }

    private Map<String, Object> setProductToNopublishedStatus(Integer idProduct, Short status) {
        Map<String, Object> response = new HashMap<>();
        try {
            Optional<MercadoLibrePublications> product = mlPublishRepository.findById(idProduct);
            if (product.isPresent()) {
                product.get().setStates(status);
                mlPublishRepository.save(product.get());
                response.put("response", "Updated");
                return response;
            }
        } catch (Exception e) {
            logger.error(ActionResult.DATABASE_ERROR.getValue() + "Error: {}" , e.getMessage());
            response.put(ActionResult.DATABASE_ERROR.getValue(), e.getMessage());
        }
        return response;
    }

    private UpdatesOfSystem getCurrentUpdateOfSystem(Long idData) {
        UpdatesOfSystem data = new UpdatesOfSystem();
        Optional<UpdatesOfSystem> optionalData = updateSysRepo.findById(idData);
        if (optionalData.isPresent()) {
            data.setId(optionalData.get().getId());
            data.setStartDate(optionalData.get().getStartDate());
            data.setEndDate(optionalData.get().getEndDate());
            data.setFinishedSync(optionalData.get().isFinishedSync());
            data.setMessage(optionalData.get().getMessage());
        }
        return data;
    }

    //Crea un publicacion en Meli
    private Map<String, Object> createPublication(Item publicationRequest) {
        Map<String, Object> response = new HashMap<>();

        try {
            response.put("response", apiService.createPublication(publicationRequest, accountMeli.get().getAccessToken()));
            return response;
        } catch (ApiException e) {
            logger.error(" Error in the system: {}", e.getResponseBody());
            response.put(MapResponseConstants.MELI_ERROR,
                  new ApiMeliModelException(e.getCode(), "Error en el sistema. Contacte al administrador del sistema"));
            return response;
        }
    }

    //Carga una descripcion a una publicacion en Meli
    private Map<String, Object> loadDescriptionToItem(DescriptionRequest product, String token, String idPublicationMeli) {
        Map<String, Object> response = new HashMap<>();
        try {
            response.put("response", apiService.loadDescription(product, token, idPublicationMeli));
            return response;
        } catch (ApiException e) {
            //comprobar los codigos de Token Vencido
            logger.error(" Error obteniendo el token de seguridad: {}", e.getResponseBody());
            response.put(MapResponseConstants.MELI_ERROR, new ApiMeliModelException(e.getCode(), e.getResponseBody()));
            return response;
        }
    }

    private boolean verifySecurityTokens(Integer accountId) throws TokenException, ApiException {
        Optional<SellerAccount> accountFounded = getAccountMeli(accountId, false);       
        try {
            if (accountFounded.isEmpty()) {
                return false;
            } else if (MeliUtils.isExpiredToken(accountFounded.get())) {
                accountFounded = Optional.ofNullable(apiService.getTokenByRefreshToken(accountFounded.get()));
                accountMeli = accountFounded;
            }
            return true;
        } catch (TokenException | ApiException e) {
            logger.error(" Error getting token Meli Response: {}", e.getMessage());
            throw e;
        }
    }

    private DetailsPublicationsMeli setPublicationFail(ItemModel item, Integer accountId) {
        DetailsPublicationsMeli detailP = null;
        if (!item.getSku().isBlank()) {
            detailP = detailsPublicationRepository.findBySKUAndAccountId(item.getSku(), accountId);
            detailP.setStatus("fail");
            detailP.setFlex(2); // fail = without parameter            
        }
        return detailP;
    }


}
