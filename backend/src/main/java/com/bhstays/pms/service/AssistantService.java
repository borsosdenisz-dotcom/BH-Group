package com.bhstays.pms.service;

import com.bhstays.pms.config.AppProperties;
import com.bhstays.pms.dto.assistant.AssistantChatResponse;
import com.bhstays.pms.dto.assistant.AssistantMessageRequest;
import com.bhstays.pms.dto.property.PriceQuoteResponse;
import com.bhstays.pms.dto.publicapi.PublicPropertySummaryResponse;
import com.bhstays.pms.dto.reservation.AvailabilityResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * FAQ chat assistant backed by the Anthropic Messages API, with read-only
 * tool-use (V3) so it can answer live availability/price questions instead
 * of guessing. V1/V2 are unchanged: stateless (the caller resends the full
 * history each turn), FAQ-only otherwise, and the [[NEEDS_HUMAN]] handoff
 * signal still applies to everything it always applied to.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final RestClient anthropicRestClient;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final PublicPropertyService publicPropertyService;
    private final PublicReservationService publicReservationService;

    // Each iteration is one Anthropic call; a tool_use response consumes one
    // iteration and triggers another. Bounded so a confused model can't loop
    // forever (cost + latency), same reasoning as the message/history caps.
    private static final int MAX_TOOL_ITERATIONS = 4;

    private static final String OUTAGE_FALLBACK_TEMPLATE =
            "Nu pot prelua acum răspunsul asistentului virtual. Te rugăm să ne contactezi %s și revenim cât mai repede.";

    // Machine-readable escalation signal: the model appends this exact
    // marker on its own line whenever it defers to a human (the same cases
    // the system prompt tells it to defer on), so the handoff flow (V2) can
    // detect "the AI couldn't help" without fragile text-matching on the
    // visible reply. Stripped before the text is ever returned to the caller.
    private static final String NEEDS_HUMAN_MARKER = "[[NEEDS_HUMAN]]";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            Ești asistentul virtual al BH Stays, o platformă de administrare și rezervare \
            de proprietăți de cazare pe termen scurt.

            Reguli stricte pe care trebuie să le respecți întotdeauna:
            - Răspunde DOAR la întrebări generale despre platformă și servicii (FAQ), \
            folosind STRICT informațiile de mai jos. Nu inventa politici, prețuri sau \
            detalii care nu apar explicit aici.
            - Pentru întrebări despre disponibilitatea sau prețul unui apartament anume, \
            la date anume (ex. „e liber apartamentul X pe 12-15 septembrie?", „cât costă \
            X pentru 2 nopți?"), FOLOSEȘTE tool-urile puse la dispoziție (find_property, \
            check_availability, get_quote) ca să răspunzi cu date REALE, live - NU inventa \
            niciodată disponibilitate sau prețuri. Dacă find_property nu găsește \
            apartamentul, cere numele exact sau mai multe detalii - nu ghici un id. Dacă \
            un tool eșuează, spune sincer că nu ai putut verifica chiar acum.
            - Există un mecanism real prin care un coleg din echipă preia conversația \
            chiar în acest chat, imediat. NU spune NICIODATĂ că nu poți conecta clientul \
            cu o persoană, că ești doar un asistent virtual și nu poți face asta, sau \
            ceva similar - e o afirmație falsă, mecanismul chiar există și funcționează.
            - Dacă clientul cere EXPLICIT să vorbească cu un om / o persoană / cineva / \
            echipa / un operator (română sau engleză), NU pune întrebări suplimentare \
            (ex. „care e problema?"), NU enumera opțiuni de contact și NU nega că poți \
            face asta - răspunde DOAR cu o propoziție scurtă și pozitivă, de tipul \
            „Te conectez imediat cu un coleg din echipă.", apoi încheie direct cu \
            marcajul de mai jos.
            - Dacă întrebarea este despre O REZERVARE EXISTENTĂ (nu despre disponibilitate \
            sau preț general - acelea le rezolvi cu tool-urile), despre datele personale \
            ale unui client, sau clientul vrea să creeze/modifice/anuleze o rezervare \
            acum, NU încerca să rezolvi tu - spune-i politicos că un coleg din echipă \
            revine către el, și oferă-i datele de contact de mai jos.
            - Dacă nu știi răspunsul sau informația nu apare mai jos (și niciun tool nu se \
            aplică), spune sincer că nu ești sigur și trimite clientul la contact - nu \
            ghici și nu inventa.
            - În oricare din cele trei cazuri de mai sus (cerere explicită de a vorbi cu \
            un om, SAU rezervare existentă/date personale, SAU nu știi răspunsul), încheie \
            răspunsul tău EXACT cu marcajul [[NEEDS_HUMAN]] pe propria linie, la final. NU \
            folosi acest marcaj pentru un răspuns FAQ normal sau pentru un răspuns bazat pe \
            tool-uri la care ai reușit să răspunzi.
            - Răspunde în limba în care scrie clientul (română sau engleză). Fii concis \
            (2-4 propoziții).

            Informații despre BH Stays:
            1. Check-in / check-out: ora de check-in și check-out variază pe proprietate - \
            apare clar pe pagina fiecărui anunț, înainte de trimiterea cererii. Unele \
            proprietăți au check-in autonom cu smart lock; altele au check-in cu personal.
            2. Anulare rezervare: fiecare proprietate are propria politică de anulare \
            (flexibilă, moderată, strictă sau nerambursabilă), afișată clar pe pagina \
            anunțului, înainte de trimiterea cererii.
            3. Facilități (inclusiv parcare): fiecare proprietate are propriul set de \
            facilități, afișat pe pagina anunțului - poți filtra din pagina de căutare \
            după facilitățile de care ai nevoie (parcare, animale de companie etc.).
            4. Animale de companie: unele proprietăți acceptă animale - poți filtra după \
            această facilitate în căutare.
            5. Comision / cost administrare (pentru proprietari): depinde de locație, \
            tipul proprietății și serviciile incluse - se stabilește transparent la prima \
            discuție, fără costuri ascunse.
            6. Curățenie: fiecare sejur include curățenie și lenjerie/prosoape curate; \
            taxa de curățenie (dacă se aplică) apare defalcat, separat, în cererea de \
            rezervare.
            7. Plată: nu se percepe nicio plată la trimiterea cererii de rezervare - \
            echipa confirmă manual cererea, iar modalitatea de plată se stabilește cu \
            echipa după confirmare.
            8. Cum rezerv: căutarea și rezervarea se fac din pagina „Vezi apartamente" - \
            alegi orașul, datele și numărul de oaspeți.
            9. Locație: proprietățile sunt în orașele afișate în căutare; adresa exactă e \
            trimisă doar după confirmarea rezervării.
            10. Listarea unei proprietăți: proprietarii pot lista o proprietate prin \
            formularul „Listează-ți proprietatea", lăsând datele de contact; echipa revine \
            cu o estimare de venit.
            11. Contact: %s
            """;

    public AssistantChatResponse chat(List<AssistantMessageRequest> history) {
        String contactFallback = buildContactFallback();

        String apiKey = appProperties.getAssistant().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ANTHROPIC_API_KEY is not configured - returning the contact fallback instead of calling the API");
            return outageFallback(contactFallback);
        }

        try {
            List<AnthropicMessage> conversation = new ArrayList<>(trimHistory(history).stream()
                    .map(m -> new AnthropicMessage(m.role(), m.content()))
                    .toList());

            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                AnthropicResponse response = callAnthropic(apiKey, contactFallback, conversation);
                List<AnthropicContentBlock> toolUses = response.content() == null ? List.of()
                        : response.content().stream().filter(b -> "tool_use".equals(b.type())).toList();

                if (toolUses.isEmpty()) {
                    String answer = extractText(response);
                    if (answer == null) {
                        return outageFallback(contactFallback);
                    }
                    boolean needsHuman = answer.contains(NEEDS_HUMAN_MARKER);
                    String cleaned = needsHuman ? answer.replace(NEEDS_HUMAN_MARKER, "").trim() : answer;
                    return new AssistantChatResponse(cleaned, needsHuman);
                }

                // Echo the model's own turn (text + tool_use blocks) back
                // verbatim, then supply the tool results as the next "user"
                // turn - this is the exact shape the Messages API requires
                // for a multi-turn tool conversation.
                conversation.add(new AnthropicMessage("assistant", response.content()));
                conversation.add(new AnthropicMessage("user",
                        toolUses.stream().map(this::executeTool).toList()));
            }

            log.warn("Assistant tool-use loop exceeded {} iterations - returning the contact fallback",
                    MAX_TOOL_ITERATIONS);
            return outageFallback(contactFallback);
        } catch (Exception ex) {
            log.error("Anthropic API call failed - returning the contact fallback", ex);
            return outageFallback(contactFallback);
        }
    }

    private AnthropicResponse callAnthropic(String apiKey, String contactFallback, List<AnthropicMessage> conversation) {
        return anthropicRestClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .body(new AnthropicRequest(
                        appProperties.getAssistant().getModel(),
                        appProperties.getAssistant().getMaxTokens(),
                        SYSTEM_PROMPT_TEMPLATE.formatted(contactFallback),
                        conversation,
                        TOOLS))
                .retrieve()
                .body(AnthropicResponse.class);
    }

    private AssistantChatResponse outageFallback(String contactFallback) {
        // The bot genuinely couldn't help at all here, so this always
        // counts as a needs-human signal too.
        return new AssistantChatResponse(OUTAGE_FALLBACK_TEMPLATE.formatted(contactFallback), true);
    }

    // ------------------------------------------------------------------
    // Tool execution - strictly read-only: only the three public lookups
    // below are ever called. No booking creation/modification/cancellation,
    // no reservation/user/owner data - those services aren't even injected.
    // ------------------------------------------------------------------

    private ToolResultBlock executeTool(AnthropicContentBlock toolUse) {
        try {
            String result = switch (toolUse.name()) {
                case "find_property" -> findProperty(toolUse.input());
                case "check_availability" -> checkAvailability(toolUse.input());
                case "get_quote" -> getQuote(toolUse.input());
                default -> throw new IllegalArgumentException("Unknown tool: " + toolUse.name());
            };
            return new ToolResultBlock("tool_result", toolUse.id(), result, null);
        } catch (Exception ex) {
            log.warn("Assistant tool '{}' failed: {}", toolUse.name(), ex.getMessage());
            String message = ex.getMessage() != null ? ex.getMessage() : "Tool call failed";
            return new ToolResultBlock("tool_result", toolUse.id(), message, true);
        }
    }

    // PropertySpecifications.search does a LIKE on the whole phrase, so a
    // natural-language query full of filler words won't match a name as a
    // substring (e.g. "apartament din cluj" doesn't LIKE-match "Apartament
    // cluj" because of "din" in the middle). These are stripped before a
    // per-word fallback search - deliberately NOT touching
    // PropertySpecifications.search itself, since that also backs the
    // public site's own search box.
    private static final Set<String> FIND_PROPERTY_STOPWORDS = Set.of(
            "din", "de", "cu", "la", "un", "o", "in", "pe", "si", "și",
            "the", "a", "an", "of", "near",
            "apartament", "apartamentul"
    );

    private String findProperty(JsonNode input) throws JsonProcessingException {
        String query = input.path("query").asText("").trim();
        if (query.isEmpty()) {
            return "No query provided - ask the visitor which property they mean.";
        }

        List<PublicPropertySummaryResponse> found = searchProperties(query);
        if (found.isEmpty()) {
            found = searchByTokens(query);
        }

        List<PropertyMatch> matches = found.stream()
                .map(p -> new PropertyMatch(p.id(), p.name(), p.city(), p.maxGuests(), p.demo()))
                .toList();
        return objectMapper.writeValueAsString(matches);
    }

    private List<PublicPropertySummaryResponse> searchProperties(String query) {
        return publicPropertyService.search(
                query, null, null, null, null, null, null, null, PageRequest.of(0, 5)).content();
    }

    /**
     * Splits the query into words, drops stopwords/filler and anything
     * under 3 characters, then searches each remaining significant word on
     * its own and merges the results (deduplicated by property id, capped
     * at 5) - so "apartament din cluj" still finds "Apartament cluj" via
     * the "cluj" token even though the full phrase doesn't LIKE-match.
     */
    private List<PublicPropertySummaryResponse> searchByTokens(String query) {
        List<String> tokens = Arrays.stream(query.toLowerCase().split("\\s+"))
                .map(t -> t.replaceAll("[^\\p{L}\\p{N}]", ""))
                .filter(t -> t.length() >= 3 && !FIND_PROPERTY_STOPWORDS.contains(t))
                .distinct()
                .toList();

        LinkedHashMap<UUID, PublicPropertySummaryResponse> merged = new LinkedHashMap<>();
        for (String token : tokens) {
            if (merged.size() >= 5) {
                break;
            }
            for (PublicPropertySummaryResponse property : searchProperties(token)) {
                merged.putIfAbsent(property.id(), property);
            }
        }
        return merged.values().stream().limit(5).toList();
    }

    private String checkAvailability(JsonNode input) throws JsonProcessingException {
        UUID propertyId = parsePropertyId(input);
        LocalDate checkIn = parseDate(input, "checkIn");
        LocalDate checkOut = parseDate(input, "checkOut");
        requireValidDateRange(checkIn, checkOut);

        AvailabilityResponse availability = publicReservationService.availability(propertyId, checkIn, checkOut);
        return objectMapper.writeValueAsString(availability);
    }

    private String getQuote(JsonNode input) throws JsonProcessingException {
        UUID propertyId = parsePropertyId(input);
        LocalDate checkIn = parseDate(input, "checkIn");
        LocalDate checkOut = parseDate(input, "checkOut");
        requireValidDateRange(checkIn, checkOut);

        int guests = input.path("guests").asInt(1);
        if (guests < 1 || guests > 20) {
            throw new IllegalArgumentException("guests must be between 1 and 20");
        }

        PriceQuoteResponse quote = publicReservationService.quote(propertyId, checkIn, checkOut, guests);
        return objectMapper.writeValueAsString(quote);
    }

    private UUID parsePropertyId(JsonNode input) {
        String raw = input.path("propertyId").asText(null);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("propertyId is required - call find_property first");
        }
        return UUID.fromString(raw);
    }

    private LocalDate parseDate(JsonNode input, String field) {
        String raw = input.path(field).asText(null);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " is required (format: YYYY-MM-DD)");
        }
        return LocalDate.parse(raw);
    }

    private void requireValidDateRange(LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn");
        }
    }

    // Compact shape for find_property results - just enough for the model
    // to pick the right property id, not the full public property payload.
    // demo=true marks a showcase listing that cannot be booked.
    private record PropertyMatch(UUID id, String name, String city, int maxGuests, boolean demo) {
    }

    // ------------------------------------------------------------------

    // Defense in depth: AssistantChatRequest already caps the history at 20
    // messages via @Size, but this keeps AssistantService safe against any
    // other future caller that skips DTO validation.
    private List<AssistantMessageRequest> trimHistory(List<AssistantMessageRequest> history) {
        int max = appProperties.getAssistant().getMaxHistoryMessages();
        return history.size() <= max ? history : history.subList(history.size() - max, history.size());
    }

    private String buildContactFallback() {
        String email = appProperties.getContact().getEmail();
        String phone = appProperties.getContact().getPhone();
        String joined = Stream.of(email, phone)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" sau la "));
        return joined.isBlank() ? "prin formularul de contact de pe pagina „Pentru proprietari”" : joined;
    }

    private String extractText(AnthropicResponse response) {
        if (response == null || response.content() == null) {
            return null;
        }
        String joined = response.content().stream()
                .filter(b -> "text".equals(b.type()))
                .map(AnthropicContentBlock::text)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("\n"));
        return joined.isBlank() ? null : joined;
    }

    // Package-private (not private) so AssistantServiceTest can stub the
    // exact response type RestClient.ResponseSpec#body(Class) deserializes to.
    record AnthropicMessage(String role, Object content) {
    }

    record AnthropicTool(String name, String description, @JsonProperty("input_schema") JsonNode inputSchema) {
    }

    record AnthropicRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            String system,
            List<AnthropicMessage> messages,
            List<AnthropicTool> tools
    ) {
    }

    // Doubles as both the inbound response block shape (text/tool_use) and
    // the outbound echo of the assistant's own tool_use turn - NON_NULL so
    // each serializes back to exactly the fields Anthropic expects per type.
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AnthropicContentBlock(String type, String text, String id, String name, JsonNode input) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ToolResultBlock(
            String type,
            @JsonProperty("tool_use_id") String toolUseId,
            String content,
            @JsonProperty("is_error") Boolean isError
    ) {
    }

    record AnthropicResponse(List<AnthropicContentBlock> content) {
    }

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();

    private static JsonNode schema(String json) {
        try {
            return SCHEMA_MAPPER.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid static tool schema", ex);
        }
    }

    // Package-private (not private) so AssistantServiceTest can assert on the
    // exposed tool set directly (guardrail: read-only, exactly these three).
    static final List<AnthropicTool> TOOLS = List.of(
            new AnthropicTool("find_property",
                    "Search for a property. Keep the query SHORT - prefer just the city or one "
                            + "distinctive word from the name (e.g. \"Cluj\", \"Central\"), NOT a full "
                            + "sentence. Returns a short list of matches (id, name, city, max guests) so you "
                            + "can pick the right property id for check_availability/get_quote. Always call "
                            + "this first if you don't already have a propertyId. If it returns no matches, "
                            + "call it again with a simpler/shorter query (e.g. just the city name) before "
                            + "telling the visitor you couldn't find it.",
                    schema("""
                            {"type":"object","properties":{"query":{"type":"string",\
                            "description":"A short search term - city or one distinctive word from the \
                            property name. Not a sentence."}},\
                            "required":["query"]}
                            """)),
            new AnthropicTool("check_availability",
                    "Check whether a specific property is available for a given date range. Use only "
                            + "after you have a propertyId (from find_property, or if the visitor already gave "
                            + "you one).",
                    schema("""
                            {"type":"object","properties":{\
                            "propertyId":{"type":"string","description":"The property's id (UUID)"},\
                            "checkIn":{"type":"string","description":"Check-in date, YYYY-MM-DD"},\
                            "checkOut":{"type":"string","description":"Check-out date, YYYY-MM-DD"}},\
                            "required":["propertyId","checkIn","checkOut"]}
                            """)),
            new AnthropicTool("get_quote",
                    "Get the real, dynamic price quote (nights, subtotal, fees, discounts, total) for a "
                            + "stay at a specific property. Use only after you have a propertyId.",
                    schema("""
                            {"type":"object","properties":{\
                            "propertyId":{"type":"string","description":"The property's id (UUID)"},\
                            "checkIn":{"type":"string","description":"Check-in date, YYYY-MM-DD"},\
                            "checkOut":{"type":"string","description":"Check-out date, YYYY-MM-DD"},\
                            "guests":{"type":"integer","description":"Number of guests, default 1"}},\
                            "required":["propertyId","checkIn","checkOut"]}
                            """))
    );
}
