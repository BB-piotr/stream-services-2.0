package com.backbase.stream.compositions.legalentity.core.service.impl;

import com.backbase.stream.compositions.integration.legalentity.api.LegalEntityIntegrationApi;
import com.backbase.stream.compositions.integration.legalentity.model.LegalEntity;
import com.backbase.stream.compositions.integration.legalentity.model.PullLegalEntityResponse;
import com.backbase.stream.compositions.legalentity.core.exception.LegalEntityCompositionException;
import com.backbase.stream.compositions.legalentity.core.model.LegalEntityIngestPullRequest;
import com.backbase.stream.compositions.legalentity.core.service.LegalEntityIntegrationService;
import com.backbase.stream.exceptions.LegalEntityException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@AllArgsConstructor
public class LegalEntityIntegrationServiceImpl implements LegalEntityIntegrationService {
    private final LegalEntityIntegrationApi legalEntityIntegrationApi;

    /**
     * {@inheritDoc}
     */
    public Mono<LegalEntity> pullLegalEntity(LegalEntityIngestPullRequest ingestPullRequest) {
        return legalEntityIntegrationApi
                .pullLegalEntity(
                        ingestPullRequest.getLegalEntityExternalId(),
                        ingestPullRequest.getAdditionalParameters())
                .onErrorResume(ex ->
                        Mono.error(
                                new LegalEntityCompositionException(
                                        String.format("Failed to pull legal entity from integration service (%s)", ex.getMessage()), ex)))

                .map(PullLegalEntityResponse::getLegalEntity);
    }
}
