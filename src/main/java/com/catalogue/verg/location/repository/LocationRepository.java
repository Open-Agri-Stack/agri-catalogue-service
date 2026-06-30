package com.catalogue.verg.location.repository;

import com.catalogue.verg.location.entity.LocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<LocationEntity, String> {

}