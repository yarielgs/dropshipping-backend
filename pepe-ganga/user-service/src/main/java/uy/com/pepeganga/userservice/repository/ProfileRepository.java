package uy.com.pepeganga.userservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import uy.com.pepeganga.business.common.entities.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Integer> {

    void deleteProfileByUserId(Integer id);

    @Query(value = "select p.* from profile p join users u where p.user_id = u.id and u.email = :email", nativeQuery = true)
    Profile findProfileByUserEmail(@Param("email") String email);

    @Transactional(readOnly = true)
    @Query(value = "select p.id from profile p join users u where p.user_id = u.id and u.enabled = 0", nativeQuery = true)
    List<Integer> findIdProfileDisabled();

    @Transactional(readOnly = true)
    @Query(value = "select p.id from profile p join users u where p.user_id = u.id and u.enabled = 1", nativeQuery = true)
    List<Integer> findIdProfileEnabled();
}
