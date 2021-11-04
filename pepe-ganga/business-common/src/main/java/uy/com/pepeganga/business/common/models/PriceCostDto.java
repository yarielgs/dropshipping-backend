package uy.com.pepeganga.business.common.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PriceCostDto {

   private String sku;

   private double priceCostUyu;

   private double priceCostUsd;
}
