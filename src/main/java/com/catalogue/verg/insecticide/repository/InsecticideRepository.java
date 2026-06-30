package com.catalogue.verg.insecticide.repository;

import com.catalogue.verg.insecticide.entity.InsecticideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsecticideRepository extends JpaRepository<InsecticideEntity, String> {

}