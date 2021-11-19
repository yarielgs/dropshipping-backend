package uy.pepeganga.meli.service.services;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import uy.com.pepeganga.business.common.entities.DetailsPublicationsMeli;
import uy.com.pepeganga.business.common.utils.enums.ActionResult;
import uy.com.pepeganga.business.common.utils.enums.MeliStatusPublications;
import uy.pepeganga.meli.service.repository.DetailsPublicationMeliRepository;
import uy.pepeganga.meli.service.utils.MapResponseConstants;

@Service
public class ScheduleProcess {

   private static final boolean PENDING = true;
   private static final int SPECIALPAUSED = 0;
   private static final int ISDELETED = 0;

   private final DetailsPublicationMeliRepository detailsPublicationRepository;

   public ScheduleProcess(final DetailsPublicationMeliRepository detailsPublicationRepository) {
      this.detailsPublicationRepository = detailsPublicationRepository;
   }
/*
   public void getPendingToUpdatePublications() {
      Map<String, Object> response = new HashMap<>();
      try {
         List<DetailsPublicationsMeli> detailsList = detailsPublicationRepository.findByPendingToUpdate(PENDING, MeliStatusPublications.ACTIVE.getValue(), SPECIALPAUSED, ISDELETED);
         if (!detailsList.isEmpty()) {


            synchronizationPublications(detailsList);

            //Actualiza las publicaciones con cambios pendientes
            List<DetailsPublicationsMeli> toUpdateList = detailsList
                  .stream()
                  .filter(d -> d.getStatus().equals(MeliStatusPublications.ACTIVE.getValue()) && d.getPendingMarginUpdate())
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
   }*/
}
