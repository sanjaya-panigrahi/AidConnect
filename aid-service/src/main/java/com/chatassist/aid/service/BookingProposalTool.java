package com.chatassist.aid.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * Request-scoped tool object used by the LLM to propose a doctor and slot pair.
 *
 * <p>Keep this class stateful but instantiated per chat request to avoid cross-request leakage.
 */
final class BookingProposalTool {

    private static final Logger log = LoggerFactory.getLogger(BookingProposalTool.class);

    private Long doctorId;
    private Long slotId;

    @Tool(name = "proposeBooking", description = "Propose a clinic booking candidate using doctorId and slotId.")
    public String proposeBooking(@ToolParam(description = "Doctor ID from clinic data") Long doctorId,
                                 @ToolParam(description = "Slot ID from clinic data") Long slotId) {
        this.doctorId = doctorId;
        this.slotId = slotId;
        log.info("Tool proposeBooking invoked with doctorId={}, slotId={}", doctorId, slotId);
        return "Proposal captured. Ask the user to confirm with yes/no.";
    }

    boolean hasProposal() {
        return doctorId != null && slotId != null;
    }

    Long getDoctorId() {
        return doctorId;
    }

    Long getSlotId() {
        return slotId;
    }
}

