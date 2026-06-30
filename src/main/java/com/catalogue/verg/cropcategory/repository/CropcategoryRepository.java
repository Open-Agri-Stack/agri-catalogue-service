package com.catalogue.verg.cropcategory.repository;

import com.catalogue.verg.cropcategory.entity.CropcategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CropcategoryRepository extends JpaRepository<CropcategoryEntity, String> {

}