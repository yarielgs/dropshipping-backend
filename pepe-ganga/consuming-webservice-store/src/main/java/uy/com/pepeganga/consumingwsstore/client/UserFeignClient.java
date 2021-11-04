package uy.com.pepeganga.consumingwsstore.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "user-service")
public interface UserFeignClient {

   @GetMapping("/api/profiles/disabled")
   List<Integer> getDisabledProfiles();

   @GetMapping("/api/profiles/enabled")
   List<Integer> getEnabledProfiles();

}
