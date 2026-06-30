package com.catalogue.verg.seed.repository;

import com.catalogue.verg.seed.entity.SeedEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeedRepository extends JpaRepository<SeedEntity, String> {

}