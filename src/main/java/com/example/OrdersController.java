package com.example;

import com.example.model.rest.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.UUID;

public class OrdersController implements Runnable {

    private static final long minQty = 10;
    private static final long minAsk = 13;
    private static final long minBid = 79;

    private static final Logger logger = LoggerFactory.getLogger(OrdersController.class);

    private final Platform marketPlugin;

    private final Random rg = new Random();

    public OrdersController(Platform marketPlugin) {
        this.marketPlugin = marketPlugin;
    }

    @Override
    public void run() {
        final var fetchedInstruments = marketPlugin.instruments();
        final var fetchedPortfolio = marketPlugin.portfolio();

        if (fetchedPortfolio instanceof PortfolioResponse.Other other) {
            logger.error("portfolio call does not return portfolio {}", other);
        }

        if (fetchedPortfolio instanceof PortfolioResponse.Portfolio portfolio && fetchedInstruments instanceof InstrumentsResponse.Instruments instruments) {
            portfolio
                    .portfolio()
                    .stream()
                    .map(PortfolioResponse.Portfolio.Element::instrument)
                    .forEach( instrument -> {
                        final var orders = marketPlugin.orders(new OrdersRequest(instrument));
                        logger.info("instrument {} has orders {}", instrument, orders);
                    });

            final var selectedForBuy = instruments
                    .available()
                    .stream()
                    .filter(pe -> rg.nextDouble() < 0.10)
                    .toList();

            selectedForBuy
                    .stream()
                    .map(pe -> {
                        final var history = marketPlugin.history(new HistoryRequest(pe));

                        long bid;
                        if (history instanceof HistoryResponse.History correct) {
                            bid = (long) (
                                    1.1 * correct
                                            .bought()
                                            .stream()
                                            .mapToLong(b -> b.offer().price())
                                            .average()
                                            .orElse(minBid)
                            );
                        } else {
                            bid = minBid;
                        }

                        final var qty = rg.nextInt((int) (portfolio.cash() / 4 / bid));
                        final var buy = new SubmitOrderRequest.Buy(pe.symbol(), UUID.randomUUID().toString(), qty, bid);

                        logger.info("order to submit {}", buy);
                        return buy;
                    })
                    .map(marketPlugin::buy)
                    .forEach(vo -> logger.info("order placed with response {}", vo));

            final var selectedForSell = portfolio
                    .portfolio()
                    .stream()
                    .filter(pe -> rg.nextDouble() < 0.20)
                    .toList();

            selectedForSell
                    .stream()
                    .map(pe -> {
                        logger.info("portfolio element {}", pe);
                        final var history = marketPlugin.history(new HistoryRequest(pe.instrument()));

                        long ask;
                        if (history instanceof HistoryResponse.History correct) {
                            ask = (long) (
                                    0.9 * correct
                                            .bought()
                                            .stream()
                                            .mapToLong(b -> b.offer().price())
                                            .average()
                                            .orElse(minAsk)
                            );
                        } else {
                            ask = minAsk;
                        }

                        final long qty = Math.min(pe.qty(), minQty);
                        final var sell = new SubmitOrderRequest.Sell(pe.instrument().symbol(), UUID.randomUUID().toString(), qty, ask);

                        logger.info("order to submit {}", sell);
                        return sell;
                    })
                    .map(marketPlugin::sell)
                    .forEach(vo -> logger.info("order placed with response {}", vo));
        }
    }
}
