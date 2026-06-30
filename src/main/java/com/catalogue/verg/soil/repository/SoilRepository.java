package com.catalogue.verg.soil.repository;

import com.catalogue.verg.soil.entity.SoilEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SoilRepository extends JpaRepository<SoilEntity, String> {

}