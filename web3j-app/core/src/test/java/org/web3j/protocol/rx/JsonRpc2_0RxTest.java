package org.web3j.protocol.rx;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.OngoingStubbing;
import rx.Observable;
import rx.Subscription;

import org.web3j.protocol.ObjectMapperFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EaiBlock;
import org.web3j.protocol.core.methods.response.EaiFilter;
import org.web3j.protocol.core.methods.response.EaiLog;
import org.web3j.protocol.core.methods.response.EaiUninstallFilter;
import org.web3j.utils.Numeric;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JsonRpc2_0RxTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.getObjectMapper();

    private Web3j web3j;

    private Web3jService web3jService;

    @Before
    public void setUp() {
        web3jService = mock(Web3jService.class);
        web3j = Web3j.build(web3jService, 1000, Executors.newSingleThreadScheduledExecutor());
    }

    @Test
    public void testReplayBlocksObservable() throws Exception {

        List<EaiBlock> eaiBlocks = Arrays.asList(createBlock(0), createBlock(1), createBlock(2));

        OngoingStubbing<EaiBlock> stubbing =
                when(web3jService.send(any(Request.class), eq(EaiBlock.class)));
        for (EaiBlock eaiBlock : eaiBlocks) {
            stubbing = stubbing.thenReturn(eaiBlock);
        }

        Observable<EaiBlock> observable = web3j.replayBlocksObservable(
                new DefaultBlockParameterNumber(BigInteger.ZERO),
                new DefaultBlockParameterNumber(BigInteger.valueOf(2)),
                false);

        CountDownLatch transactionLatch = new CountDownLatch(eaiBlocks.size());
        CountDownLatch completedLatch = new CountDownLatch(1);

        List<EaiBlock> results = new ArrayList<>(eaiBlocks.size());
        Subscription subscription = observable.subscribe(
                result -> {
                    results.add(result);
                    transactionLatch.countDown();
                },
                throwable -> fail(throwable.getMessage()),
                () -> completedLatch.countDown());

        transactionLatch.await(1, TimeUnit.SECONDS);
        assertThat(results, equalTo(eaiBlocks));

        subscription.unsubscribe();

        completedLatch.await(1, TimeUnit.SECONDS);
        assertTrue(subscription.isUnsubscribed());
    }

    @Test
    public void testReplayBlocksDescendingObservable() throws Exception {

        List<EaiBlock> eaiBlocks = Arrays.asList(createBlock(2), createBlock(1), createBlock(0));

        OngoingStubbing<EaiBlock> stubbing =
                when(web3jService.send(any(Request.class), eq(EaiBlock.class)));
        for (EaiBlock eaiBlock : eaiBlocks) {
            stubbing = stubbing.thenReturn(eaiBlock);
        }

        Observable<EaiBlock> observable = web3j.replayBlocksObservable(
                new DefaultBlockParameterNumber(BigInteger.ZERO),
                new DefaultBlockParameterNumber(BigInteger.valueOf(2)),
                false, false);

        CountDownLatch transactionLatch = new CountDownLatch(eaiBlocks.size());
        CountDownLatch completedLatch = new CountDownLatch(1);

        List<EaiBlock> results = new ArrayList<>(eaiBlocks.size());
        Subscription subscription = observable.subscribe(
                result -> {
                    results.add(result);
                    transactionLatch.countDown();
                },
                throwable -> fail(throwable.getMessage()),
                () -> completedLatch.countDown());

        transactionLatch.await(1, TimeUnit.SECONDS);
        assertThat(results, equalTo(eaiBlocks));

        subscription.unsubscribe();

        completedLatch.await(1, TimeUnit.SECONDS);
        assertTrue(subscription.isUnsubscribed());
    }

    @Test
    public void testCatchUpToLatestAndSubscribeToNewBlockObservable() throws Exception {
        List<EaiBlock> expected = Arrays.asList(
                createBlock(0), createBlock(1), createBlock(2),
                createBlock(3), createBlock(4), createBlock(5),
                createBlock(6));

        List<EaiBlock> eaiBlocks = Arrays.asList(
                expected.get(2),  // greatest block
                expected.get(0), expected.get(1), expected.get(2),
                expected.get(4), // greatest block
                expected.get(3), expected.get(4),
                expected.get(4),  // greatest block
                expected.get(5),  // initial response from eaiGetFilterLogs call
                expected.get(6)); // subsequent block from new block observable

        OngoingStubbing<EaiBlock> stubbing =
                when(web3jService.send(any(Request.class), eq(EaiBlock.class)));
        for (EaiBlock eaiBlock : eaiBlocks) {
            stubbing = stubbing.thenReturn(eaiBlock);
        }

        EaiFilter eaiFilter = objectMapper.readValue(
                "{\n"
                        + "  \"id\":1,\n"
                        + "  \"jsonrpc\": \"2.0\",\n"
                        + "  \"result\": \"0x1\"\n"
                        + "}", EaiFilter.class);
        EaiLog eaiLog = objectMapper.readValue(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":["
                        + "\"0x31c2342b1e0b8ffda1507fbffddf213c4b3c1e819ff6a84b943faabb0ebf2403\""
                        + "]}",
                EaiLog.class);
        EaiUninstallFilter eaiUninstallFilter = objectMapper.readValue(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":true}", EaiUninstallFilter.class);

        when(web3jService.send(any(Request.class), eq(EaiFilter.class)))
                .thenReturn(eaiFilter);
        when(web3jService.send(any(Request.class), eq(EaiLog.class)))
                .thenReturn(eaiLog);
        when(web3jService.send(any(Request.class), eq(EaiUninstallFilter.class)))
                .thenReturn(eaiUninstallFilter);

        Observable<EaiBlock> observable = web3j.catchUpToLatestAndSubscribeToNewBlocksObservable(
                new DefaultBlockParameterNumber(BigInteger.ZERO),
                false);

        CountDownLatch transactionLatch = new CountDownLatch(expected.size());
        CountDownLatch completedLatch = new CountDownLatch(1);

        List<EaiBlock> results = new ArrayList<>(expected.size());
        Subscription subscription = observable.subscribe(
                result -> {
                    results.add(result);
                    transactionLatch.countDown();
                },
                throwable -> fail(throwable.getMessage()),
                () -> completedLatch.countDown());

        transactionLatch.await(1250, TimeUnit.MILLISECONDS);
        assertThat(results, equalTo(expected));

        subscription.unsubscribe();

        completedLatch.await(1, TimeUnit.SECONDS);
        assertTrue(subscription.isUnsubscribed());
    }

    private EaiBlock createBlock(int number) {
        EaiBlock eaiBlock = new EaiBlock();
        EaiBlock.Block block = new EaiBlock.Block();
        block.setNumber(Numeric.encodeQuantity(BigInteger.valueOf(number)));

        eaiBlock.setResult(block);
        return eaiBlock;
    }
}
