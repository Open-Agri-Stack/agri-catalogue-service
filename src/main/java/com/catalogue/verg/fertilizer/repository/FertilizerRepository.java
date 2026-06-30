package com.catalogue.verg.fertilizer.repository;

import com.catalogue.verg.fertilizer.entity.FertilizerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FertilizerRepository extends JpaRepository<FertilizerEntity, String> {

}