package com.chatassist.aid.service;

import com.chatassist.aid.entity.AidConversationState;
import com.chatassist.aid.entity.AppointmentBooking;
import com.chatassist.aid.entity.Doctor;
import com.chatassist.aid.entity.DoctorAvailability;
import com.chatassist.aid.repository.AidConversationStateRepository;
import com.chatassist.aid.repository.AppointmentBookingRepository;
import com.chatassist.aid.repository.DoctorAvailabilityRepository;
import com.chatassist.common.dto.ChatMessageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the simplified LLM-driven {@link AppointmentAssistantService}.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Greeting on blank / reset message</li>
 *   <li>Fallback when no LLM client is wired (no API key)</li>
 *   <li>Confirmation YES: booking persisted and state reset</li>
 *   <li>Confirmation NO: state cleared, friendly cancel message returned</li>
 *   <li>Confirmation when slot was taken: graceful message and state cleared</li>
 *   <li>DB context builder lists doctors and slot IDs correctly</li>
 * </ul>
 */
class AppointmentAssistantServiceTest {

    private static final Long DOCTOR_ID = 1L;
    private static final Long SLOT_ID   = 101L;

    private final Doctor            doctor = new Doctor(DOCTOR_ID, "dr-smith", "Dr. Smith", "Cardiology", true);
    private final LocalDateTime     slotAt = LocalDateTime.of(2026, 4, 5, 10, 0);
    private final DoctorAvailability slot  = new DoctorAvailability(SLOT_ID, doctor, slotAt, true);

    private Map<String, AidConversationState> stateStore;
    private List<AppointmentBooking>          bookings;
    private boolean                            slotExists;
    private boolean                            alreadyBooked;

    private AppointmentAssistantService serviceNoLlm;   // chatClient == null
    private AppointmentAssistantService serviceWithLlm; // chatClient wired (stub)

    @BeforeEach
    void setUp() {
        stateStore    = new HashMap<>();
        bookings      = new ArrayList<>();
        slotExists    = true;
        alreadyBooked = false;

        serviceNoLlm   = build(/* llm stub */ null);
        serviceWithLlm = build(buildFakeChatClient("Great choice! I suggest Dr. Smith on Apr 5 at 10:00 AM."));
    }

    // ── 1. Greeting on blank / reset ─────────────────────────────────────────

    @Test
    void blankMessageReturnsGreeting() {
        String reply = serviceNoLlm.respond("alice", "");
        assertThat(reply).contains("Hello").contains("appointment assistant");
    }

    @Test
    void resetCommandReturnsGreeting() {
        String reply = serviceNoLlm.respond("alice", "reset");
        assertThat(reply).contains("Hello");
    }

    // ── 2. Fallback when no LLM ───────────────────────────────────────────────

    @Test
    void noLlmClientReturnsFriendlyUnavailableMessage() {
        String reply = serviceNoLlm.respond("alice", "I need a cardiologist");
        assertThat(reply).contains("not configured");
    }

    // ── 3. Confirmation YES — booking is persisted ────────────────────────────

    @Test
    void confirmationYesPersistsBookingAndResetsState() {
        // Seed state: user is in CONFIRMING stage with proposed slot
        AidConversationState state = new AidConversationState("bob", AppointmentAssistantService.STAGE_CONFIRMING);
        state.setDoctorId(DOCTOR_ID);
        state.setProposedSlot(slotAt);
        stateStore.put("bob", state);

        String reply = serviceNoLlm.respond("bob", "yes");

        assertThat(reply).contains("Booked").contains("Dr. Smith").contains("Cardiology");
        assertThat(bookings).hasSize(1);
        assertThat(bookings.get(0).getPatientUsername()).isEqualTo("bob");
        assertThat(stateStore.get("bob").getStage()).isEqualTo("CHATTING");
    }

    // ── 4. Confirmation NO — state cleared ───────────────────────────────────

    @Test
    void confirmationNoClearsStateAndReturnsCancelMessage() {
        AidConversationState state = new AidConversationState("carol", AppointmentAssistantService.STAGE_CONFIRMING);
        state.setDoctorId(DOCTOR_ID);
        state.setProposedSlot(slotAt);
        stateStore.put("carol", state);

        String reply = serviceNoLlm.respond("carol", "no");

        assertThat(reply).contains("No problem");
        assertThat(bookings).isEmpty();
        assertThat(stateStore.get("carol").getDoctorId()).isNull();
        assertThat(stateStore.get("carol").getProposedSlot()).isNull();
    }

    // ── 5. Slot taken between proposal and confirmation ───────────────────────

    @Test
    void confirmationYesWhenSlotTakenReturnsSorryMessage() {
        alreadyBooked = true;   // simulate slot taken by another user

        AidConversationState state = new AidConversationState("dave", AppointmentAssistantService.STAGE_CONFIRMING);
        state.setDoctorId(DOCTOR_ID);
        state.setProposedSlot(slotAt);
        stateStore.put("dave", state);

        String reply = serviceNoLlm.respond("dave", "yes");

        assertThat(reply).contains("just taken");
        assertThat(bookings).isEmpty();
        assertThat(stateStore.get("dave").getStage()).isEqualTo("CHATTING");
    }

    // ── 6. DB context builder ─────────────────────────────────────────────────

    @Test
    void buildDbContextListsDoctorAndSlotIds() {
        String ctx = serviceNoLlm.buildDbContext();

        assertThat(ctx).contains("Dr. Smith");
        assertThat(ctx).contains("Cardiology");
        assertThat(ctx).contains("Doctor ID: 1");
        assertThat(ctx).contains("Slot ID: 101");
    }

    // ── 7. Tool-based proposal keeps user-visible reply clean ─────────────────

    @Test
    void toolBasedProposalKeepsReplyClean() {
        String reply = serviceWithLlm.respond("eve", "book me a slot with Dr. Smith");

        assertThat(reply).doesNotContain("[PROPOSE:");
        assertThat(reply).contains("Dr. Smith");
    }

    // ── 8. State transitions to CONFIRMING after LLM propose ─────────────────

    @Test
    void stateTransitionsToConfirmingWhenLlmProposesSlot() {
        serviceWithLlm.respond("frank", "book me a slot with Dr. Smith");

        AidConversationState state = stateStore.get("frank");
        assertThat(state).isNotNull();
        assertThat(state.getStage()).isEqualTo(AppointmentAssistantService.STAGE_CONFIRMING);
        assertThat(state.getDoctorId()).isEqualTo(DOCTOR_ID);
        assertThat(state.getProposedSlot()).isEqualTo(slotAt);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds the service wired with in-memory repository stubs and an optional
     * ChatClient stub (null ⟹ no-LLM path).
     */
    private AppointmentAssistantService build(ChatClient chatClientStub) {

        AidConversationStateRepository stateRepository = proxy(
                AidConversationStateRepository.class, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "findByUsername" -> Optional.ofNullable(stateStore.get((String) args[0]));
                        case "save" -> {
                            AidConversationState s = (AidConversationState) args[0];
                            stateStore.put(s.getUsername(), s);
                            yield s;
                        }
                        default -> defaultValue(method.getReturnType());
                    };
                });

        AppointmentBookingRepository bookingRepository = proxy(
                AppointmentBookingRepository.class, (proxy, method, args) -> {
                    if ("save".equals(method.getName())) {
                        bookings.add((AppointmentBooking) args[0]);
                        return args[0];
                    }
                    if ("existsByDoctorIdAndAppointmentTimeAndStatus".equals(method.getName())) {
                        return alreadyBooked;
                    }
                    return defaultValue(method.getReturnType());
                });

        DoctorAvailabilityRepository availabilityRepository = proxy(
                DoctorAvailabilityRepository.class, (proxy, method, args) -> {
                    if ("existsByDoctorIdAndAvailableAtAndEnabledTrue".equals(method.getName())) {
                        return slotExists;
                    }
                    return defaultValue(method.getReturnType());
                });

        DoctorCacheService doctorCacheService = new DoctorCacheService(null, null) {
            @Override public List<Doctor>           findActiveDoctors()                          { return List.of(doctor); }
            @Override public Optional<Doctor>       findDoctorById(Long id)                      { return DOCTOR_ID.equals(id) ? Optional.of(doctor) : Optional.empty(); }
            @Override public List<DoctorAvailability> findUpcomingSlots(Long id, LocalDateTime f) { return DOCTOR_ID.equals(id) ? List.of(slot) : List.of(); }
            @Override public Optional<DoctorAvailability> findSlotById(Long id)                  { return SLOT_ID.equals(id) ? Optional.of(slot) : Optional.empty(); }
            @Override public void evictDoctorSlots(Long id) { /* no-op */ }
        };

        ChatHistoryClient chatHistoryClient = new ChatHistoryClient(
                null,
                new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
                null,
                null,
                false,
                "http://localhost:8082") {
            @Override
            public List<com.chatassist.common.dto.ChatMessageResponse> getConversationHistory(String username, String aidUsername) {
                return List.of(); // No history in tests
            }

            @Override
            public void appendExchange(String username, ChatMessageResponse userMessage, ChatMessageResponse aidReply) {
                // no-op for unit tests
            }
        };

        ObjectProvider<ChatClient.Builder> builderProvider;
        if (chatClientStub != null) {
            ChatClient.Builder builder = proxy(ChatClient.Builder.class, (p, m, a) ->
                    "build".equals(m.getName()) ? chatClientStub : p);
            StaticListableBeanFactory factory = new StaticListableBeanFactory();
            factory.addBean("chatClientBuilder", builder);
            builderProvider = factory.getBeanProvider(ChatClient.Builder.class);
        } else {
            builderProvider = new StaticListableBeanFactory().getBeanProvider(ChatClient.Builder.class);
        }

        return new AppointmentAssistantService(
                availabilityRepository,
                bookingRepository,
                stateRepository,
                doctorCacheService,
                chatHistoryClient,
                builderProvider);
    }

    /** Creates a ChatClient stub whose prompt()…call().content() returns the given reply. */
    private ChatClient buildFakeChatClient(String reply) {
        return (ChatClient) Proxy.newProxyInstance(
                ChatClient.class.getClassLoader(),
                new Class[]{ChatClient.class},
                (proxy, method, args) -> {
                    if ("prompt".equals(method.getName())) {
                        return buildPromptChainProxy(reply);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    /** Returns a deep-stub chain: prompt().system(…).user(…).call().content() → reply. */
    private Object buildPromptChainProxy(String reply) {
        return Proxy.newProxyInstance(
                ChatClient.ChatClientRequestSpec.class.getClassLoader(),
                new Class[]{ChatClient.ChatClientRequestSpec.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "system", "user" -> proxy;
                    case "tools" -> {
                        maybeInvokeTool(args);
                        yield proxy;
                    }
                    case "call"           -> buildCallProxy(reply);
                    default               -> defaultValue(method.getReturnType());
                });
    }

    /** Invokes proposeBooking on the passed tool object if present. */
    private void maybeInvokeTool(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return;
        }
        Object toolCandidate = args[0];
        if (toolCandidate instanceof Object[] arr && arr.length > 0) {
            toolCandidate = arr[0];
        }
        if (toolCandidate == null) {
            return;
        }
        try {
            var method = toolCandidate.getClass().getDeclaredMethod("proposeBooking", Long.class, Long.class);
            method.setAccessible(true);
            method.invoke(toolCandidate, DOCTOR_ID, SLOT_ID);
        } catch (ReflectiveOperationException ignored) {
            // Non-tool calls in this proxy are fine to ignore.
        }
    }

    private Object buildCallProxy(String reply) {
        return Proxy.newProxyInstance(
                ChatClient.CallResponseSpec.class.getClassLoader(),
                new Class[]{ChatClient.CallResponseSpec.class},
                (proxy, method, args) ->
                        "content".equals(method.getName()) ? reply : defaultValue(method.getReturnType()));
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> iface, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class[]{iface}, handler);
    }

    private Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class)     return 0;
        if (type == long.class)    return 0L;
        return null;
    }
}

