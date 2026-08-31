package com.catalogue.verg.core.util;


public class Constants{

    public static final String ACTIVE = "ACTIVE";
    public static final String IN_ACTIVE = "INACTIVE";
    public static final String SUCCESSFULLY_CREATED = "successfully created";
    public static final String SUCCESSFULLY_UPDATED = "successfully updated";
    public static final String SUCCESSFULLY_DELETED = "successfully deleted";
    public static final String RESULT = "result";
    public static final String FAILED_CONST = "FAILED";
    public static final String ID = "id";

    public static final String ERROR = "ERROR";
    public static final String REDIS_KEY_PREFIX = "verg_cache_";

    // Lifecycle / approval workflow constants
    // Note: ACTIVE (published/live) is reused; IN_ACTIVE now means deactivated (toggle);
    // DELETED (below) is the soft-delete state set by delete().
    public static final String DRAFT = "DRAFT";
    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REWORK = "REWORK";
    public static final String REJECTED = "REJECTED";
    public static final String DELETED = "DELETED";
    public static final String PUBLISHED = "PUBLISHED"; // accepted alias for ACTIVE on the review endpoint
    public static final String STATUS = "status";
    public static final String CREATED_ON = "createdOn";
    public static final String UPDATED_ON = "updatedOn";
    public static final String INVALID_STATUS = "Invalid target status";
    public static final String INVALID_STATUS_TRANSITION = "Record is not in a state that allows this action";
    // Error code returned when a lifecycle endpoint is hit on a catalogue whose lifecycle is switched off
    public static final String LIFECYCLE_DISABLED = "LIFECYCLE_DISABLED";

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

    
    // Audit Specific Constants
    public static final String AUDIT_VALIDATION_FILE_JSON = "/payloadValidation/auditPayloadValidation.json";
    public static final String AUDIT_ID_RQST = "auditId";
    public static final String AUDIT_INDEX_NAME = "audit_index";

    
    // Placeholder actor identity for audit records until real user context is threaded through.
    public static final String AUDIT_DEFAULT_USER_ID = "ANONYMOUS";
    public static final String AUDIT_DEFAULT_USER_NAME = "ANONYMOUS";
    public static final String AUDIT_DEFAULT_USER_ROLE = "SYSTEM";

    
    // Seed Specific Constants
    public static final String SEED_VALIDATION_FILE_JSON = "/payloadValidation/seedPayloadValidation.json";
    public static final String SEED_ID_RQST = "seedId";
    public static final String SEED_INDEX_NAME = "seed_index";

    
    // Croptype Specific Constants
    public static final String CROPTYPE_VALIDATION_FILE_JSON = "/payloadValidation/croptypePayloadValidation.json";
    public static final String CROPTYPE_ID_RQST = "croptypeId";
    public static final String CROPTYPE_INDEX_NAME = "croptype_index";

    
    // Cropvariety Specific Constants
    public static final String CROPVARIETY_VALIDATION_FILE_JSON = "/payloadValidation/cropvarietyPayloadValidation.json";
    public static final String CROPVARIETY_ID_RQST = "cropvarietyId";
    public static final String CROPVARIETY_INDEX_NAME = "cropvariety_index";

    
    // Cropcategory Specific Constants
    public static final String CROPCATEGORY_VALIDATION_FILE_JSON = "/payloadValidation/cropcategoryPayloadValidation.json";
    public static final String CROPCATEGORY_ID_RQST = "cropcategoryId";
    public static final String CROPCATEGORY_INDEX_NAME = "cropcategory_index";

    
    // Livestock Specific Constants
    public static final String LIVESTOCK_VALIDATION_FILE_JSON = "/payloadValidation/livestockPayloadValidation.json";
    public static final String LIVESTOCK_ID_RQST = "livestockId";
    public static final String LIVESTOCK_INDEX_NAME = "livestock_index";

    
    // Livestockbreed Specific Constants
    public static final String LIVESTOCKBREED_VALIDATION_FILE_JSON = "/payloadValidation/livestockbreedPayloadValidation.json";
    public static final String LIVESTOCKBREED_ID_RQST = "livestockbreedId";
    public static final String LIVESTOCKBREED_INDEX_NAME = "livestockbreed_index";

    
    // Livestockcategory Specific Constants
    public static final String LIVESTOCKCATEGORY_VALIDATION_FILE_JSON = "/payloadValidation/livestockcategoryPayloadValidation.json";
    public static final String LIVESTOCKCATEGORY_ID_RQST = "livestockcategoryId";
    public static final String LIVESTOCKCATEGORY_INDEX_NAME = "livestockcategory_index";

    
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

    
    // Locationobject Specific Constants
    public static final String LOCATIONOBJECT_VALIDATION_FILE_JSON = "/payloadValidation/locationobjectPayloadValidation.json";
    public static final String LOCATIONOBJECT_ID_RQST = "locationobjectId";
    public static final String LOCATIONOBJECT_INDEX_NAME = "locationobject_index";

    
    // Locationmapper Specific Constants
    public static final String LOCATIONMAPPER_VALIDATION_FILE_JSON = "/payloadValidation/locationmapperPayloadValidation.json";
    public static final String LOCATIONMAPPER_ID_RQST = "locationmapperId";
    public static final String LOCATIONMAPPER_INDEX_NAME = "locationmapper_index";

    
    // Locationconfig Specific Constants
    public static final String LOCATIONCONFIG_VALIDATION_FILE_JSON = "/payloadValidation/locationconfigPayloadValidation.json";
    public static final String LOCATIONCONFIG_ID_RQST = "locationconfigId";
    public static final String LOCATIONCONFIG_INDEX_NAME = "locationconfig_index";

    
    // Marketplace Specific Constants
    public static final String MARKETPLACE_VALIDATION_FILE_JSON = "/payloadValidation/marketplacePayloadValidation.json";
    public static final String MARKETPLACE_ID_RQST = "marketplaceId";
    public static final String MARKETPLACE_INDEX_NAME = "marketplace_index";

        private Constants() {
    }
}