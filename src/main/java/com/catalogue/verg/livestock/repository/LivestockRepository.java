package com.catalogue.verg.livestock.repository;

import com.catalogue.verg.livestock.entity.LivestockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LivestockRepository extends JpaRepository<LivestockEntity, String> {

}