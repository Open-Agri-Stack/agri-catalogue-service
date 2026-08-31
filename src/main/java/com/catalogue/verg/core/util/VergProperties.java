package com.catalogue.verg.core.util;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class VergProperties {

        @Value("${spring.redis.cacheTtl}")
        private long searchResultRedisTtl;

        @Value("${search.string.max.regex.length}")
        private int searchStringMaxRegexLength;
        @Value("${elastic.required.field.audit.json.path}")
        private String elasticAuditJsonPath;
    
        @Value("${elastic.required.field.seed.json.path}")
        private String elasticSeedJsonPath;
    
        @Value("${elastic.required.field.croptype.json.path}")
        private String elasticCroptypeJsonPath;
    
        @Value("${elastic.required.field.cropvariety.json.path}")
        private String elasticCropvarietyJsonPath;
    
        @Value("${elastic.required.field.cropcategory.json.path}")
        private String elasticCropcategoryJsonPath;
    
        @Value("${elastic.required.field.livestock.json.path}")
        private String elasticLivestockJsonPath;
    
        @Value("${elastic.required.field.livestockbreed.json.path}")
        private String elasticLivestockbreedJsonPath;
    
        @Value("${elastic.required.field.livestockcategory.json.path}")
        private String elasticLivestockcategoryJsonPath;
    
        @Value("${elastic.required.field.season.json.path}")
        private String elasticSeasonJsonPath;
    
        @Value("${elastic.required.field.soil.json.path}")
        private String elasticSoilJsonPath;
    
        @Value("${elastic.required.field.extensionequipment.json.path}")
        private String elasticExtensionequipmentJsonPath;
    
        @Value("${elastic.required.field.pesticide.json.path}")
        private String elasticPesticideJsonPath;
    
        @Value("${elastic.required.field.insecticide.json.path}")
        private String elasticInsecticideJsonPath;
    
        @Value("${elastic.required.field.fertilizer.json.path}")
        private String elasticFertilizerJsonPath;
    
        @Value("${elastic.required.field.locationobject.json.path}")
        private String elasticLocationobjectJsonPath;
    
        @Value("${elastic.required.field.locationmapper.json.path}")
        private String elasticLocationmapperJsonPath;
    
        @Value("${elastic.required.field.locationconfig.json.path}")
        private String elasticLocationconfigJsonPath;
    
        @Value("${elastic.required.field.marketplace.json.path}")
        private String elasticMarketplaceJsonPath;
    }
