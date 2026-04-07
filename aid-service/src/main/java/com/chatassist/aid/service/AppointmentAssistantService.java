package com.chatassist.aid.service;

import com.chatassist.aid.entity.AidConversationState;
import com.chatassist.aid.entity.AppointmentBooking;
import com.chatassist.aid.entity.Doctor;
import com.chatassist.aid.entity.DoctorAvailability;
import com.chatassist.aid.repository.AidConversationStateRepository;
import com.chatassist.aid.repository.AppointmentBookingRepository;
import com.chatassist.aid.repository.DoctorAvailabilityRepository;
import com.chatassist.common.dto.ChatMessageResponse;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Conversational appointment assistant powered by an LLM.
 *
 * <p><b>Flow</b>
 * <pre>
 *  CHATTING  ─── user asks anything ──► LLM answers using live DB context
 *                                        │
 *                         LLM calls tool proposeBooking(doctorId, slotId)
 *                                        │
 *            ◄── store proposal ─────────┘
 *  CONFIRMING ── "yes" ──► book in DB ──► reset to CHATTING
 *             └── "no"  ──► cancel    ──► reset to CHATTING
 * </pre>
 *
 * <p>The actual INSERT into {@code appointment_bookings} always goes through the
 * repository (deterministic, transactional). The LLM only drives the conversation
 * and proposes a slot; it never writes to the DB directly.
 *
 * <p>When no {@link ChatClient} is available (no API key configured) the service
 * returns a friendly unavailability message instead of throwing.
 */
@Service
public class AppointmentAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentAssistantService.class);

    static final String STAGE_CHATTING   = "CHATTING";
    static final String STAGE_CONFIRMING = "CONFIRMING";

    private static final DateTimeFormatter DISPLAY_DT =
            DateTimeFormatter.ofPattern("EEE, MMM d 'at' h:mm a", Locale.ENGLISH);

    private static final String SYSTEM_PROMPT = """
            You are a friendly appointment booking assistant for Hinjawadi Mega Clinic.

            Rules:
            - Use ONLY the clinic data provided below. Never invent doctors, specialties, dates, or slot IDs.
            - Keep replies short and conversational.
            - When the user wants to book an appointment, pick the best matching slot from the data,
              describe it clearly (doctor name, specialty, date and time), and ask the user to confirm.
            - If you are proposing a booking, call the tool named proposeBooking with numeric doctorId and slotId.
            - Do not include [PROPOSE:...] markers in user-visible text.
            - IMPORTANT: Maintain conversation context and flow. Remember what the user has said before
              and what you have replied. Do NOT reset or restart the conversation unless the user explicitly
              says "reset", "start over", or similar.
            """;

    private final DoctorAvailabilityRepository availabilityRepository;
    private final AppointmentBookingRepository bookingRepository;
    private final AidConversationStateRepository stateRepository;
    private final DoctorCacheService doctorCacheService;
    private final ChatHistoryClient chatHistoryClient;
    private final ChatClient chatClient;

    public AppointmentAssistantService(DoctorAvailabilityRepository availabilityRepository,
                                       AppointmentBookingRepository bookingRepository,
                                       AidConversationStateRepository stateRepository,
                                       DoctorCacheService doctorCacheService,
                                       ChatHistoryClient chatHistoryClient,
                                       ObjectProvider<ChatClient.Builder> builderProvider) {
        this.availabilityRepository = availabilityRepository;
        this.bookingRepository      = bookingRepository;
        this.stateRepository        = stateRepository;
        this.doctorCacheService     = doctorCacheService;
        this.chatHistoryClient      = chatHistoryClient;
        ChatClient.Builder builder  = builderProvider.getIfAvailable();
        this.chatClient             = (builder != null) ? builder.build() : null;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    @Transactional
    public String respond(String username, String prompt) {
        String message = (prompt == null ? "" : prompt.trim());

        AidConversationState state = stateRepository.findByUsername(username)
                .orElseGet(() -> stateRepository.save(
                        new AidConversationState(username, STAGE_CHATTING)));

        // Global reset
        if (message.isBlank()
                || "reset".equalsIgnoreCase(message)
                || "start".equalsIgnoreCase(message)) {
            state.resetToGreeting();
            stateRepository.save(state);
            return greet();
        }

        // Awaiting confirmation for a previously proposed slot
        if (STAGE_CONFIRMING.equals(state.getStage())
                && state.getDoctorId() != null
                && state.getProposedSlot() != null) {
            return handleConfirmation(state, message);
        }

        // Default: conversational LLM with live DB context
        return handleChat(state, message);
    }

    // ── Confirmation stage ────────────────────────────────────────────────────

    private String handleConfirmation(AidConversationState state, String message) {
        if (isAffirmative(message)) {
            Doctor doctor = doctorCacheService.findDoctorById(state.getDoctorId()).orElse(null);

            if (doctor != null && isBookable(doctor.getId(), state.getProposedSlot())) {
                bookingRepository.save(new AppointmentBooking(
                        doctor, state.getUsername(), state.getProposedSlot(), "BOOKED"));
                doctorCacheService.evictDoctorSlots(doctor.getId());

                String confirmedAt = state.getProposedSlot().format(DISPLAY_DT);
                state.resetToGreeting();
                stateRepository.save(state);

                return "✅ Booked! Your appointment with **" + doctor.getDisplayName()
                        + "** (" + doctor.getSpecialty() + ") on " + confirmedAt
                        + " is confirmed. See you then! 🎉";
            }

            // Slot was taken between proposal and confirmation
            state.setStage(STAGE_CHATTING);
            state.setDoctorId(null);
            state.setProposedSlot(null);
            stateRepository.save(state);
            return "Sorry, that slot was just taken. Ask me again and I'll find another available time.";
        }

        if (isNegative(message)) {
            state.setStage(STAGE_CHATTING);
            state.setDoctorId(null);
            state.setProposedSlot(null);
            stateRepository.save(state);
            return "No problem! Let me know if you'd like a different doctor or time slot.";
        }

        return "Please reply **yes** to confirm the appointment or **no** to cancel.";
    }

    // ── Chat stage — LLM with DB context ─────────────────────────────────────

    private String handleChat(AidConversationState state, String message) {
        if (chatClient == null) {
            return "I'm sorry, the AI assistant is not configured. "
                    + "Please contact the clinic directly to book an appointment.";
        }

        String dbContext = buildDbContext();
        String conversationHistory = buildConversationHistory(state.getUsername());
        BookingProposalTool proposalTool = new BookingProposalTool();

        try {
            String raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT + "\n\nClinic data:\n" + dbContext 
                            + (conversationHistory.isEmpty() ? "" : "\n\nPrevious conversation:\n" + conversationHistory))
                    .tools(proposalTool)
                    .user(message)
                    .call()
                    .content();

            if (raw == null || raw.isBlank()) {
                return fallback();
            }

            if (proposalTool.hasProposal()) {
                applyProposal(state, proposalTool.getDoctorId(), proposalTool.getSlotId());
                return raw.strip();
            }

            if (isLikelyBookingIntent(message)) {
                log.info("Booking intent detected without tool proposal for user '{}'; requesting clearer preferences", state.getUsername());
                return "I can help book this appointment. Please share your preferred doctor or specialty and a preferred date/time window, and I will propose an available slot for confirmation.";
            }


            return raw.strip();

        } catch (Exception ex) {
            log.warn("LLM call failed in aid-service: {}", ex.getMessage());
            return fallback();
        }
    }

    private void applyProposal(AidConversationState state, long doctorId, long slotId) {
        Doctor doctor = doctorCacheService.findDoctorById(doctorId).orElse(null);
        DoctorAvailability slot = doctorCacheService.findSlotById(slotId).orElse(null);

        if (doctor != null && slot != null && slot.isEnabled()) {
            state.setStage(STAGE_CONFIRMING);
            state.setDoctorId(doctorId);
            state.setProposedSlot(slot.getAvailableAt());
            stateRepository.save(state);
        }
    }

    // ── DB context builder ────────────────────────────────────────────────────

    /**
     * Build conversation history for context. Fetches last messages from chat-service
     * and formats them for the LLM.
     */
    private String buildConversationHistory(String username) {
        try {
            List<ChatMessageResponse> history = chatHistoryClient.getConversationHistory(username, "aid");
            if (history == null || history.isEmpty()) {
                return "";
            }

            // Keep only the last 20 messages to avoid context overflow
            List<ChatMessageResponse> recentMessages = history.stream()
                    .skip(Math.max(0, history.size() - 20))
                    .toList();

            StringBuilder sb = new StringBuilder();
            for (ChatMessageResponse msg : recentMessages) {
                String sender = msg.senderUsername();
                String content = msg.content();
                
                // Format as "User: message" or "Assistant: message"
                if ("aid".equalsIgnoreCase(sender)) {
                    sb.append("Assistant: ").append(content).append("\n");
                } else {
                    sb.append("User: ").append(content).append("\n");
                }
            }
            return sb.toString().strip();
        } catch (Exception ex) {
            log.debug("Failed to build conversation history: {}", ex.getMessage());
            return "";
        }
    }

    String buildDbContext() {
        List<Doctor> doctors = doctorCacheService.findActiveDoctors();
        if (doctors.isEmpty()) {
            return "No doctors are currently available.";
        }

        StringBuilder sb = new StringBuilder();
        for (Doctor doctor : doctors) {
            sb.append("Doctor: ").append(doctor.getDisplayName())
              .append(" | Specialty: ").append(doctor.getSpecialty())
              .append(" | Doctor ID: ").append(doctor.getId()).append("\n");

            List<DoctorAvailability> slots =
                    doctorCacheService.findUpcomingSlots(doctor.getId(), LocalDateTime.now());

            if (slots.isEmpty()) {
                sb.append("  No upcoming slots available.\n");
            } else {
                for (DoctorAvailability slot : slots) {
                    sb.append("  Slot ID: ").append(slot.getId())
                      .append(" | ").append(slot.getAvailableAt().format(DISPLAY_DT)).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isBookable(Long doctorId, LocalDateTime slot) {
        return availabilityRepository.existsByDoctorIdAndAvailableAtAndEnabledTrue(doctorId, slot)
                && !bookingRepository.existsByDoctorIdAndAppointmentTimeAndStatus(doctorId, slot, "BOOKED");
    }

    private boolean isAffirmative(String s) {
        String t = s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
        return t.equals("yes") || t.equals("y") || t.equals("ok") || t.equals("sure")
                || t.equals("confirm") || t.equals("confirmed")
                || t.equals("book") || t.equals("book it");
    }

    private boolean isNegative(String s) {
        String t = s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
        return t.equals("no") || t.equals("n") || t.equals("cancel") || t.equals("nope");
    }

    private boolean isLikelyBookingIntent(String s) {
        String t = s == null ? "" : s.toLowerCase(Locale.ROOT).trim();
        if (t.isEmpty()) {
            return false;
        }
        return t.contains("book")
                || t.contains("appointment")
                || t.contains("schedule")
                || t.contains("slot")
                || t.contains("available")
                || t.contains("doctor");
    }

    private String greet() {
        return "👋 Hello! I'm the Hinjawadi Mega Clinic appointment assistant. "
                + "Ask me anything about our doctors or available slots, "
                + "or just tell me when you'd like to book an appointment.";
    }

    private String fallback() {
        return "I'm having trouble responding right now. "
                + "Please try again or contact the clinic directly.";
    }

    static final class BookingProposalTool {
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
}
