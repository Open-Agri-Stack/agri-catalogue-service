package com.catalogue.verg.pesticide.repository;

import com.catalogue.verg.pesticide.entity.PesticideEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PesticideRepository extends JpaRepository<PesticideEntity, String> {

}