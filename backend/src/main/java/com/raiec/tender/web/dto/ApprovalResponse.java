package com.raiec.tender.web.dto;

/** Result of an approve/reject action, including how many LAR records changed. */
public record ApprovalResponse(
        String tenderNo,
        String status,
        int larAdded,
        int larUpdated
) {
}
