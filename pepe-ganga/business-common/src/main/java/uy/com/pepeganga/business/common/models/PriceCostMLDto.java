package uy.com.pepeganga.business.common.models;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PriceCostMLDto {

   private List<Integer> idProfileList;

   private List<PriceCostDto> priceCostDtos;
}
