package com.bhstays.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bhstays.pms.common.response.PageResponse;
import com.bhstays.pms.config.AppProperties;
import com.bhstays.pms.dto.assistant.AssistantChatResponse;
import com.bhstays.pms.dto.assistant.AssistantMessageRequest;
import com.bhstays.pms.dto.property.PriceQuoteResponse;
import com.bhstays.pms.dto.publicapi.PublicPropertySummaryResponse;
import com.bhstays.pms.dto.reservation.AvailabilityResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private RestClient.RequestBodySpec requestBodySpec;
    @Mock
    private RestClient.ResponseSpec responseSpec;
    @Mock
    private PublicPropertyService publicPropertyService;
    @Mock
    private PublicReservationService publicReservationService;

    private AppProperties appProperties;
    private AssistantService assistantService;
    private final ObjectMapper jsonMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        appProperties.getAssistant().setApiKey("test-key");
        appProperties.getAssistant().setModel("claude-haiku-4-5-20251001");
        appProperties.getAssistant().setBaseUrl("https://api.anthropic.com");
        appProperties.getAssistant().setMaxTokens(400);
        appProperties.getAssistant().setMaxHistoryMessages(16);
        appProperties.getAssistant().setTimeoutMs(15000);

        assistantService = new AssistantService(
                restClient, appProperties, jsonMapper, publicPropertyService, publicReservationService);
    }

    private void mockRestClientChain() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(AssistantService.AnthropicRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    private AssistantService.AnthropicContentBlock textBlock(String text) {
        return new AssistantService.AnthropicContentBlock("text", text, null, null, null);
    }

    private AssistantService.AnthropicContentBlock toolUseBlock(String id, String name, JsonNode input) {
        return new AssistantService.AnthropicContentBlock("tool_use", null, id, name, input);
    }

    @Test
    void chat_returnsTheAssistantsAnswerOnSuccess() {
        mockRestClientChain();
        var mockResponse = new AssistantService.AnthropicResponse(
                List.of(textBlock("Check-in-ul variază pe proprietate.")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class)).thenReturn(mockResponse);

        AssistantChatResponse reply = assistantService.chat(
                List.of(new AssistantMessageRequest("user", "Care e ora de check-in?")));

        assertThat(reply.message()).isEqualTo("Check-in-ul variază pe proprietate.");
        assertThat(reply.needsHuman()).isFalse();
    }

    @Test
    void chat_setsNeedsHumanAndStripsTheMarker_whenTheModelFlagsIt() {
        mockRestClientChain();
        var mockResponse = new AssistantService.AnthropicResponse(
                List.of(textBlock("Nu pot confirma detalii despre rezervarea ta.\n[[NEEDS_HUMAN]]")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class)).thenReturn(mockResponse);

        AssistantChatResponse reply = assistantService.chat(
                List.of(new AssistantMessageRequest("user", "Care e statusul rezervării mele #1234?")));

        assertThat(reply.needsHuman()).isTrue();
        assertThat(reply.message()).doesNotContain("[[NEEDS_HUMAN]]");
        assertThat(reply.message()).contains("Nu pot confirma detalii despre rezervarea ta.");
    }

    @Test
    void chat_returnsContactFallbackAndNeedsHuman_whenTheApiCallThrows() {
        mockRestClientChain();
        when(responseSpec.body(AssistantService.AnthropicResponse.class))
                .thenThrow(new RuntimeException("boom"));

        AssistantChatResponse reply = assistantService.chat(List.of(new AssistantMessageRequest("user", "Salut")));

        assertThat(reply.message()).contains("Nu pot prelua acum răspunsul");
        assertThat(reply.needsHuman()).isTrue();
    }

    @Test
    void chat_returnsContactFallbackAndNeedsHuman_whenTheApiKeyIsNotConfigured_noApiCallAttempted() {
        appProperties.getAssistant().setApiKey("");

        AssistantChatResponse reply = assistantService.chat(List.of(new AssistantMessageRequest("user", "Salut")));

        assertThat(reply.message()).contains("Nu pot prelua acum răspunsul");
        assertThat(reply.needsHuman()).isTrue();
    }

    @Test
    void chat_trimsHistoryToTheConfiguredMaxBeforeCallingTheApi() {
        appProperties.getAssistant().setMaxHistoryMessages(2);
        mockRestClientChain();
        var mockResponse = new AssistantService.AnthropicResponse(List.of(textBlock("ok")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class)).thenReturn(mockResponse);

        List<AssistantMessageRequest> history = List.of(
                new AssistantMessageRequest("user", "one"),
                new AssistantMessageRequest("assistant", "two"),
                new AssistantMessageRequest("user", "three")
        );

        assistantService.chat(history);

        ArgumentCaptor<AssistantService.AnthropicRequest> captor =
                ArgumentCaptor.forClass(AssistantService.AnthropicRequest.class);
        verify(requestBodySpec).body(captor.capture());
        assertThat(captor.getValue().messages()).hasSize(2);
        assertThat(captor.getValue().messages().get(0).content()).isEqualTo("two");
        assertThat(captor.getValue().messages().get(1).content()).isEqualTo("three");
    }

    @Test
    void chat_systemPromptDeclaresTheHandoffMechanismExistsAndForbidsDenyingIt() {
        mockRestClientChain();
        var mockResponse = new AssistantService.AnthropicResponse(List.of(textBlock("ok")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class)).thenReturn(mockResponse);

        assistantService.chat(List.of(new AssistantMessageRequest("user", "Salut")));

        ArgumentCaptor<AssistantService.AnthropicRequest> captor =
                ArgumentCaptor.forClass(AssistantService.AnthropicRequest.class);
        verify(requestBodySpec).body(captor.capture());
        String systemPrompt = captor.getValue().system();

        assertThat(systemPrompt)
                .contains("Există un mecanism real prin care un coleg din echipă preia conversația")
                .contains("NU spune NICIODATĂ că nu poți conecta clientul cu o persoană")
                .contains("cere EXPLICIT să vorbească cu un om")
                .contains("[[NEEDS_HUMAN]]");
    }

    @Test
    void chat_explicitHumanRequestReply_isShortHasTheMarkerAndNeverDeniesTheHandoff() {
        mockRestClientChain();
        var mockResponse = new AssistantService.AnthropicResponse(
                List.of(textBlock("Te conectez imediat cu un coleg din echipă.\n[[NEEDS_HUMAN]]")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class)).thenReturn(mockResponse);

        AssistantChatResponse reply = assistantService.chat(
                List.of(new AssistantMessageRequest("user", "Vreau să vorbesc cu o persoană")));

        assertThat(reply.needsHuman()).isTrue();
        assertThat(reply.message()).doesNotContain("[[NEEDS_HUMAN]]");
        assertThat(reply.message().length()).isLessThan(80);
        assertThat(reply.message().toLowerCase())
                .doesNotContain("nu pot conecta")
                .doesNotContain("nu te pot conecta")
                .doesNotContain("doar un asistent virtual");
    }

    @Test
    void chat_fallsBackToTheGenericContactPointerWhenNoContactInfoIsConfigured() {
        appProperties.getAssistant().setApiKey("");
        appProperties.getContact().setEmail(null);
        appProperties.getContact().setPhone(null);

        AssistantChatResponse reply = assistantService.chat(List.of(new AssistantMessageRequest("user", "Salut")));

        assertThat(reply.message()).contains("formularul de contact");
    }

    // ------------------------------------------------------------------
    // V3: tool-use
    // ------------------------------------------------------------------

    @Test
    void tools_exposeOnlyTheThreeReadOnlyLookups_noWriteCapability() {
        List<String> names = AssistantService.TOOLS.stream().map(AssistantService.AnthropicTool::name).toList();

        assertThat(names).containsExactlyInAnyOrder("find_property", "check_availability", "get_quote");
        assertThat(names).noneMatch(n -> n.toLowerCase().matches(".*(book|cancel|create|update|delete|modify).*"));
    }

    @Test
    void chat_modelRequestsCheckAvailability_calledWithParsedParameters_toolResultFedBackForAFinalAnswer() {
        mockRestClientChain();
        UUID propertyId = UUID.randomUUID();
        JsonNode input = jsonMapper.createObjectNode()
                .put("propertyId", propertyId.toString())
                .put("checkIn", "2026-09-01")
                .put("checkOut", "2026-09-05");

        var toolUseResponse = new AssistantService.AnthropicResponse(
                List.of(toolUseBlock("toolu_1", "check_availability", input)));
        var finalResponse = new AssistantService.AnthropicResponse(
                List.of(textBlock("Da, e liber în acea perioadă!")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class))
                .thenReturn(toolUseResponse, finalResponse);
        when(publicReservationService.availability(propertyId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5)))
                .thenReturn(new AvailabilityResponse(true));

        AssistantChatResponse reply = assistantService.chat(
                List.of(new AssistantMessageRequest("user", "E liber apartamentul X pe 1-5 septembrie?")));

        assertThat(reply.message()).isEqualTo("Da, e liber în acea perioadă!");
        assertThat(reply.needsHuman()).isFalse();
        verify(publicReservationService)
                .availability(propertyId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 5));
    }

    @Test
    void chat_getQuoteResult_isSerializedIntoTheToolResultSentBackToTheModel() {
        mockRestClientChain();
        UUID propertyId = UUID.randomUUID();
        JsonNode input = jsonMapper.createObjectNode()
                .put("propertyId", propertyId.toString())
                .put("checkIn", "2026-09-01")
                .put("checkOut", "2026-09-03")
                .put("guests", 2);

        var toolUseResponse = new AssistantService.AnthropicResponse(
                List.of(toolUseBlock("toolu_2", "get_quote", input)));
        var finalResponse = new AssistantService.AnthropicResponse(
                List.of(textBlock("Costă 350 RON în total.")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class))
                .thenReturn(toolUseResponse, finalResponse);

        PriceQuoteResponse quote = new PriceQuoteResponse(
                true, null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), 2,
                new BigDecimal("300"), BigDecimal.ZERO, new BigDecimal("50"),
                null, BigDecimal.ZERO, new BigDecimal("350"), "RON", null, null);
        when(publicReservationService.quote(propertyId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), 2))
                .thenReturn(quote);

        AssistantChatResponse reply = assistantService.chat(
                List.of(new AssistantMessageRequest("user", "Cât costă apartamentul X, 1-3 septembrie, 2 persoane?")));

        assertThat(reply.message()).isEqualTo("Costă 350 RON în total.");

        ArgumentCaptor<AssistantService.AnthropicRequest> captor =
                ArgumentCaptor.forClass(AssistantService.AnthropicRequest.class);
        verify(requestBodySpec, times(2)).body(captor.capture());
        AssistantService.AnthropicRequest secondRequest = captor.getAllValues().get(1);
        @SuppressWarnings("unchecked")
        List<AssistantService.ToolResultBlock> toolResults = (List<AssistantService.ToolResultBlock>)
                secondRequest.messages().get(secondRequest.messages().size() - 1).content();
        assertThat(toolResults).hasSize(1);
        assertThat(toolResults.get(0).content()).contains("350");
        assertThat(toolResults.get(0).isError()).isNull();
    }

    @Test
    void chat_findProperty_callsSearchWithTheQueryAndReturnsACompactMatchList() {
        mockRestClientChain();
        JsonNode input = jsonMapper.createObjectNode().put("query", "apartament centru");

        var toolUseResponse = new AssistantService.AnthropicResponse(
                List.of(toolUseBlock("toolu_3", "find_property", input)));
        var finalResponse = new AssistantService.AnthropicResponse(
                List.of(textBlock("Am găsit Apartament Central.")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class))
                .thenReturn(toolUseResponse, finalResponse);

        UUID propertyId = UUID.randomUUID();
        PublicPropertySummaryResponse summary = new PublicPropertySummaryResponse(
                propertyId, "Apartament Central", "Cluj-Napoca", "Cluj", null, null,
                null, 2, 1, 4, new BigDecimal("250"), "RON", null, null, false);
        when(publicPropertyService.search(eq("apartament centru"), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 5, 1, 1, true, true));

        AssistantChatResponse reply = assistantService.chat(
                List.of(new AssistantMessageRequest("user", "Ce zici de apartamentul din centru?")));

        assertThat(reply.message()).isEqualTo("Am găsit Apartament Central.");
        verify(publicPropertyService)
                .search(eq("apartament centru"), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void chat_findProperty_fallsBackToTokenSearch_whenTheFullPhraseMatchesNothing() {
        mockRestClientChain();
        JsonNode input = jsonMapper.createObjectNode().put("query", "apartament din cluj");

        var toolUseResponse = new AssistantService.AnthropicResponse(
                List.of(toolUseBlock("toolu_5", "find_property", input)));
        var finalResponse = new AssistantService.AnthropicResponse(
                List.of(textBlock("Am găsit Apartament cluj.")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class))
                .thenReturn(toolUseResponse, finalResponse);

        UUID propertyId = UUID.randomUUID();
        PublicPropertySummaryResponse summary = new PublicPropertySummaryResponse(
                propertyId, "Apartament cluj", "Cluj", "Cluj", null, null,
                null, 1, 1, 2, new BigDecimal("200"), "RON", null, null, false);

        // The full natural-language phrase matches nothing (LIKE on the
        // whole phrase fails because of "din" in the middle) ...
        when(publicPropertyService.search(
                eq("apartament din cluj"), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0, true, true));
        // ... but the token fallback searches the single remaining
        // significant word ("apartament"/"din" are stopwords) and finds it.
        when(publicPropertyService.search(eq("cluj"), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(summary), 0, 5, 1, 1, true, true));

        AssistantChatResponse reply = assistantService.chat(
                List.of(new AssistantMessageRequest("user", "E liber apartamentul din cluj?")));

        assertThat(reply.message()).isEqualTo("Am găsit Apartament cluj.");
        verify(publicPropertyService)
                .search(eq("cluj"), any(), any(), any(), any(), any(), any(), any(), any());

        ArgumentCaptor<AssistantService.AnthropicRequest> captor =
                ArgumentCaptor.forClass(AssistantService.AnthropicRequest.class);
        verify(requestBodySpec, times(2)).body(captor.capture());
        AssistantService.AnthropicRequest secondRequest = captor.getAllValues().get(1);
        @SuppressWarnings("unchecked")
        List<AssistantService.ToolResultBlock> toolResults = (List<AssistantService.ToolResultBlock>)
                secondRequest.messages().get(secondRequest.messages().size() - 1).content();
        assertThat(toolResults.get(0).content()).contains("Apartament cluj");
    }

    @Test
    void chat_findProperty_returnsEmptyList_whenNothingMatchesEvenAfterTheTokenFallback() {
        mockRestClientChain();
        JsonNode input = jsonMapper.createObjectNode().put("query", "un loc undeva pierdut");

        var toolUseResponse = new AssistantService.AnthropicResponse(
                List.of(toolUseBlock("toolu_6", "find_property", input)));
        var finalResponse = new AssistantService.AnthropicResponse(
                List.of(textBlock("Nu am găsit niciun apartament - poți da mai multe detalii?")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class))
                .thenReturn(toolUseResponse, finalResponse);
        when(publicPropertyService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0, true, true));

        AssistantChatResponse reply = assistantService.chat(
                List.of(new AssistantMessageRequest("user", "Aveți un loc undeva pierdut?")));

        assertThat(reply.message()).isEqualTo("Nu am găsit niciun apartament - poți da mai multe detalii?");

        ArgumentCaptor<AssistantService.AnthropicRequest> captor =
                ArgumentCaptor.forClass(AssistantService.AnthropicRequest.class);
        verify(requestBodySpec, times(2)).body(captor.capture());
        AssistantService.AnthropicRequest secondRequest = captor.getAllValues().get(1);
        @SuppressWarnings("unchecked")
        List<AssistantService.ToolResultBlock> toolResults = (List<AssistantService.ToolResultBlock>)
                secondRequest.messages().get(secondRequest.messages().size() - 1).content();
        assertThat(toolResults.get(0).content()).isEqualTo("[]");
    }

    @Test
    void chat_toolWithInvalidPropertyId_returnsAnErrorToolResultInsteadOfCrashing() {
        mockRestClientChain();
        JsonNode badInput = jsonMapper.createObjectNode()
                .put("propertyId", "not-a-uuid")
                .put("checkIn", "2026-09-01")
                .put("checkOut", "2026-09-05");

        var toolUseResponse = new AssistantService.AnthropicResponse(
                List.of(toolUseBlock("toolu_4", "check_availability", badInput)));
        var finalResponse = new AssistantService.AnthropicResponse(
                List.of(textBlock("Nu am putut verifica - poți confirma numele apartamentului?")));
        when(responseSpec.body(AssistantService.AnthropicResponse.class))
                .thenReturn(toolUseResponse, finalResponse);

        AssistantChatResponse reply = assistantService.chat(
                List.of(new AssistantMessageRequest("user", "E liber apartamentul?")));

        assertThat(reply.message()).isEqualTo("Nu am putut verifica - poți confirma numele apartamentului?");
        verify(publicReservationService, never()).availability(any(), any(), any());

        ArgumentCaptor<AssistantService.AnthropicRequest> captor =
                ArgumentCaptor.forClass(AssistantService.AnthropicRequest.class);
        verify(requestBodySpec, times(2)).body(captor.capture());
        AssistantService.AnthropicRequest secondRequest = captor.getAllValues().get(1);
        @SuppressWarnings("unchecked")
        List<AssistantService.ToolResultBlock> toolResults = (List<AssistantService.ToolResultBlock>)
                secondRequest.messages().get(secondRequest.messages().size() - 1).content();
        assertThat(toolResults.get(0).isError()).isTrue();
    }

    @Test
    void chat_toolLoopStopsAtTheIterationLimit_doesNotHangForever() {
        mockRestClientChain();
        JsonNode input = jsonMapper.createObjectNode().put("query", "apartament");
        var alwaysToolUse = new AssistantService.AnthropicResponse(
                List.of(toolUseBlock("toolu_x", "find_property", input)));
        when(responseSpec.body(AssistantService.AnthropicResponse.class)).thenReturn(alwaysToolUse);
        when(publicPropertyService.search(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 5, 0, 0, true, true));

        AssistantChatResponse reply = assistantService.chat(
                List.of(new AssistantMessageRequest("user", "Ce apartamente aveți?")));

        assertThat(reply.needsHuman()).isTrue();
        assertThat(reply.message()).contains("Nu pot prelua acum răspunsul");
        verify(requestBodySpec, times(4)).body(any(AssistantService.AnthropicRequest.class));
    }
}
