package uy.com.pepeganga.userservice.service;

import uy.com.pepeganga.business.common.entities.Profile;

import java.util.List;

public interface IProfileService {

    List<Profile> getProfiles();

    Profile findProfileByUserEmail(String email);

    List<Integer> getIdProfilesDisabled();

    List<Integer> getIdProfilesEnabled();
}
