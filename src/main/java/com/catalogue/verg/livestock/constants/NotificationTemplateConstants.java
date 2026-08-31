package com.catalogue.verg.livestock.constants;

import java.util.Map;

public final class NotificationTemplateConstants {

    private NotificationTemplateConstants() {
    }

    public static final NotificationTemplate NEW_RECORD_SUBMITTED_FOR_REVIEW =
            new NotificationTemplate(
                    "Livestock",
                    "NEW_RECORD_SUBMITTED_FOR_REVIEW_LIVESTOCK"
            );

    public static final NotificationTemplate RECORD_SENT_BACK_FOR_CORRECTION =
            new NotificationTemplate(
                    "Livestock",
                    "RECORD_SENT_BACK_FOR_CORRECTION_LIVESTOCK"
            );

    public static final NotificationTemplate RECORD_RESUBMITTED_FOR_REVIEW =
            new NotificationTemplate(
                    "Livestock",
                    "RECORD_RESUBMITTED_FOR_REVIEW_LIVESTOCK"
            );

    public static final NotificationTemplate RECORD_REJECTED_BY_SUPERVISOR =
            new NotificationTemplate(
                    "Livestock",
                    "RECORD_REJECTED_BY_SUPERVISOR_LIVESTOCK"
            );

    public static final NotificationTemplate RECORD_APPROVED_BY_SUPERVISOR =
            new NotificationTemplate(
                    "Livestock",
                    "RECORD_APPROVED_BY_SUPERVISOR_LIVESTOCK"
            );

    public static final NotificationTemplate RECORD_REVIEWED_BY_ADMIN_L2 =
            new NotificationTemplate(
                    "Livestock",
                    "RECORD_REVIEWED_BY_ADMIN_L2_LIVESTOCK"
            );

    public static final NotificationTemplate RECORD_REJECTED_BY_ADMIN_L2 =
            new NotificationTemplate(
                    "Livestock",
                    "RECORD_REJECTED_BY_ADMIN_L2_LIVESTOCK"
            );

    public static final NotificationTemplate RECORD_APPROVED_BY_ADMIN_L2 =
            new NotificationTemplate(
                    "Livestock",
                    "RECORD_APPROVED_BY_ADMIN_L2_LIVESTOCK"
            );

    public static final Map<String, NotificationTemplate> TEMPLATES = Map.of(
            "NEW_RECORD_SUBMITTED_FOR_REVIEW", NEW_RECORD_SUBMITTED_FOR_REVIEW,
            "RECORD_SENT_BACK_FOR_CORRECTION", RECORD_SENT_BACK_FOR_CORRECTION,
            "RECORD_RESUBMITTED_FOR_REVIEW", RECORD_RESUBMITTED_FOR_REVIEW,
            "RECORD_REJECTED_BY_SUPERVISOR", RECORD_REJECTED_BY_SUPERVISOR,
            "RECORD_APPROVED_BY_SUPERVISOR", RECORD_APPROVED_BY_SUPERVISOR,
            "RECORD_REVIEWED_BY_ADMIN_L2", RECORD_REVIEWED_BY_ADMIN_L2,
            "RECORD_REJECTED_BY_ADMIN_L2", RECORD_REJECTED_BY_ADMIN_L2,
            "RECORD_APPROVED_BY_ADMIN_L2", RECORD_APPROVED_BY_ADMIN_L2
    );

}
