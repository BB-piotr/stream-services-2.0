package com.backbase.stream.compositions.transaction.core.service.impl;

import com.backbase.dbs.transaction.api.service.v2.model.TransactionsPostRequestBody;
import com.backbase.dbs.transaction.api.service.v2.model.TransactionsPostResponseBody;
import com.backbase.stream.TransactionService;
import com.backbase.stream.compositions.transaction.core.mapper.TransactionMapper;
import com.backbase.stream.compositions.transaction.core.model.TransactionIngestPullRequest;
import com.backbase.stream.compositions.transaction.core.model.TransactionIngestPushRequest;
import com.backbase.stream.compositions.transaction.core.model.TransactionIngestResponse;
import com.backbase.stream.compositions.transaction.core.service.TransactionIngestionService;
import com.backbase.stream.compositions.transaction.core.service.TransactionIntegrationService;
import com.backbase.stream.transaction.TransactionTask;
import com.backbase.stream.worker.model.UnitOfWork;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
@AllArgsConstructor
public class TransactionIngestionServiceImpl implements TransactionIngestionService {
    private final TransactionMapper mapper;
    private final TransactionService transactionService;
    private final TransactionIntegrationService productIntegrationService;

    /**
     * Ingests transactions in pull mode.
     *
     * @param ingestPullRequest Ingest pull request
     * @return TransactionIngestResponse
     */
    public Mono<TransactionIngestResponse> ingestPull(Mono<TransactionIngestPullRequest> ingestPullRequest) {
        return ingestPullRequest
                .map(this::pullTransactions)
                .flatMap(this::sendToDbs)
                .doOnSuccess(this::handleSuccess)
                .map(this::buildResponse);
    }

    /**
     * Ingests product group in push mode.
     *
     * @param ingestPushRequest Ingest push request
     * @return ProductIngestResponse
     */
    public Mono<TransactionIngestResponse> ingestPush(Mono<TransactionIngestPushRequest> ingestPushRequest) {
        throw new UnsupportedOperationException();
    }

    /**
     * Pulls and remap product group from integration service.
     *
     * @param request TransactionIngestPullRequest
     * @return Flux<TransactionsPostRequestBody>
     */
    private Flux<TransactionsPostRequestBody> pullTransactions(TransactionIngestPullRequest request) {
        return productIntegrationService
                .pullTransactions(request)
                .map(mapper::mapIntegrationToStream);
    }

    /**
     * Ingests transactions to DBS.
     *
     * @param transactions Transactions
     * @return Ingested transactions
     */
    private Mono<List<TransactionsPostResponseBody>> sendToDbs(Flux<TransactionsPostRequestBody> transactions) {
        List<TransactionsPostResponseBody> response = new ArrayList<TransactionsPostResponseBody>();
        List<TransactionsPostRequestBody> transactionslist = transactions.collectList().block();
        int partitionSize = 20;

        Collection<List<TransactionsPostRequestBody>> partitionedList = IntStream.range(0, transactionslist.size())
                .boxed()
                .collect(Collectors.groupingBy(partition -> (partition / partitionSize), Collectors.mapping(elementIndex -> transactionslist.get(elementIndex), Collectors.toList())))
                .values();

        for (Collection<List<TransactionsPostRequestBody>> trx : partitionedList ) {
            List<TransactionsPostResponseBody> responseBodies = transactionService.processTransactions(Flux.fromIterable(trx))
                    .flatMapIterable(UnitOfWork::getStreamTasks)
                    .flatMapIterable(TransactionTask::getResponse)
                    .collectList();
            response.addAll(responseBodies);
        }

        return Mono.just(response);
    }

    private TransactionIngestResponse buildResponse(List<TransactionsPostResponseBody> transactions) {
        return TransactionIngestResponse.builder()
                .transactions(transactions)
                .build();
    }

    private void handleSuccess(List<TransactionsPostResponseBody> transactions) {
        log.error("Transactions ingestion completed");
        if (log.isDebugEnabled()) {
            log.debug("Ingested transactions: {}", transactions);
        }
    }
}
