package com.catalogue.verg.core.util;


public class Constants{

    public static final String ACTIVE = "ACTIVE";
    public static final String IN_ACTIVE = "INACTIVE";
    public static final String SUCCESSFULLY_CREATED = "successfully created";
    public static final String SUCCESSFULLY_DELETED = "successfully deleted";
    public static final String RESULT = "result";
    public static final String FAILED_CONST = "FAILED";
    public static final String ID = "id";

    public static final String ERROR = "ERROR";
    public static final String REDIS_KEY_PREFIX = "verg_cache_";

    //ES Specific Constants
    public static final String INDEX_TYPE = "_doc";
    public static final String KEYWORD = ".keyword";
    public static final String ASC = "asc";
    public static final String MUST= "must";
    public static final String FILTER= "filter";
    public static final String MUST_NOT="must_not";
    public static final String SHOULD= "should";
    public static final String BOOL="bool";
    public static final String TERM="term";
    public static final String TERMS="terms";
    public static final String MATCH="match";
    public static final String RANGE="range";
    public static final String UNSUPPORTED_QUERY="Unsupported query type";
    public static final String UNSUPPORTED_RANGE= "Unsupported range condition";
    public static final String FACETS = "facets";
    public static final String COUNT = "count";
    public static final String SEARCH_OPERATION_LESS_THAN = "<";
    public static final String SEARCH_OPERATION_GREATER_THAN = ">";
    public static final String SEARCH_OPERATION_LESS_THAN_EQUALS = "<=";
    public static final String SEARCH_OPERATION_GREATER_THAN_EQUALS = ">=";

    public static final String SUCCESSFULLY_READING = "successfully read";
    public static final String ID_NOT_FOUND = "Id not found";
    public static final String INVALID_ID = "Invalid Id";

    public static final String FETCH_RESULT_CONSTANT = ".fetchResult:";
    public static final String URI_CONSTANT = "URI: ";

    public static final String REQUEST_PAYLOAD = "requestPayload";
    public static final String JWT_SECRET_KEY = "demand_search_result";

    public static final String REQUEST_CONSTANT = "Request: ";
    public static final String RESPONSE_CONSTANT = "Response: ";
    public static final String REQUEST = "request";

    public static final String RESPONSE = "response";
    public static final String SUCCESS = "success";
    public static final String FAILED = "Failed";
    public static final String ERROR_MESSAGE = "errmsg";

    // Entity Specific Constants
    public static final String SAMPLE_VALIDATION_FILE_JSON = "/payloadValidation/samplePayloadValidation.json";
    public static final String SAMPLE_ID_RQST = "sampleId";
    public static final String INTEREST_INDEX_NAME = "sampleIndex";

    
    // Seed Specific Constants
    public static final String SEED_VALIDATION_FILE_JSON = "/payloadValidation/seedPayloadValidation.json";
    public static final String SEED_ID_RQST = "seedId";
    public static final String SEED_INDEX_NAME = "seed_index";

    
    // Croptype Specific Constants
    public static final String CROPTYPE_VALIDATION_FILE_JSON = "/payloadValidation/croptypePayloadValidation.json";
    public static final String CROPTYPE_ID_RQST = "croptypeId";
    public static final String CROPTYPE_INDEX_NAME = "croptype_index";

    
    // Cropcategory Specific Constants
    public static final String CROPCATEGORY_VALIDATION_FILE_JSON = "/payloadValidation/cropcategoryPayloadValidation.json";
    public static final String CROPCATEGORY_ID_RQST = "cropcategoryId";
    public static final String CROPCATEGORY_INDEX_NAME = "cropcategory_index";

    
    // Cropvariety Specific Constants
    public static final String CROPVARIETY_VALIDATION_FILE_JSON = "/payloadValidation/cropvarietyPayloadValidation.json";
    public static final String CROPVARIETY_ID_RQST = "cropvarietyId";
    public static final String CROPVARIETY_INDEX_NAME = "cropvariety_index";

    
    // Livestock Specific Constants
    public static final String LIVESTOCK_VALIDATION_FILE_JSON = "/payloadValidation/livestockPayloadValidation.json";
    public static final String LIVESTOCK_ID_RQST = "livestockId";
    public static final String LIVESTOCK_INDEX_NAME = "livestock_index";

    
    // Season Specific Constants
    public static final String SEASON_VALIDATION_FILE_JSON = "/payloadValidation/seasonPayloadValidation.json";
    public static final String SEASON_ID_RQST = "seasonId";
    public static final String SEASON_INDEX_NAME = "season_index";

    
    // Soil Specific Constants
    public static final String SOIL_VALIDATION_FILE_JSON = "/payloadValidation/soilPayloadValidation.json";
    public static final String SOIL_ID_RQST = "soilId";
    public static final String SOIL_INDEX_NAME = "soil_index";

    
    // Extensionequipment Specific Constants
    public static final String EXTENSIONEQUIPMENT_VALIDATION_FILE_JSON = "/payloadValidation/extensionequipmentPayloadValidation.json";
    public static final String EXTENSIONEQUIPMENT_ID_RQST = "extensionequipmentId";
    public static final String EXTENSIONEQUIPMENT_INDEX_NAME = "extensionequipment_index";

    
    // Pesticide Specific Constants
    public static final String PESTICIDE_VALIDATION_FILE_JSON = "/payloadValidation/pesticidePayloadValidation.json";
    public static final String PESTICIDE_ID_RQST = "pesticideId";
    public static final String PESTICIDE_INDEX_NAME = "pesticide_index";

    
    // Insecticide Specific Constants
    public static final String INSECTICIDE_VALIDATION_FILE_JSON = "/payloadValidation/insecticidePayloadValidation.json";
    public static final String INSECTICIDE_ID_RQST = "insecticideId";
    public static final String INSECTICIDE_INDEX_NAME = "insecticide_index";

    
    // Fertilizer Specific Constants
    public static final String FERTILIZER_VALIDATION_FILE_JSON = "/payloadValidation/fertilizerPayloadValidation.json";
    public static final String FERTILIZER_ID_RQST = "fertilizerId";
    public static final String FERTILIZER_INDEX_NAME = "fertilizer_index";

    
    // Location Specific Constants
    public static final String LOCATION_VALIDATION_FILE_JSON = "/payloadValidation/locationPayloadValidation.json";
    public static final String LOCATION_ID_RQST = "locationId";
    public static final String LOCATION_INDEX_NAME = "location_index";

        private Constants() {
    }
}